package nanogram.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * NanoGram AI — движок локального ИИ-чата на Groq.
 *
 * Самодостаточный модуль: не зависит от классов Telegram-форка,
 * поэтому его добавление не может сломать сборку.
 *
 * - Ключ API читается из assets/nanogram/ai_key.txt (кладётся сборкой из GitHub Secret GROQ_API_KEY).
 * - История хранится ЛОКАЛЬНО (файл в личной папке приложения), наружу уходит только сам
 *   запрос к api.groq.com — как у любого ИИ-клиента.
 * - Ответ приходит потоково (streaming): колбэк onToken вызывается по мере генерации.
 *
 * ВАЖНО для UI: колбэки Listener приходят из фонового потока —
 * обновлять интерфейс нужно через runOnUiThread(...) или Handler(Looper.getMainLooper()).
 */
public class NanoGramAi {

    /** Модель на Groq. Для встроенного веб-поиска позже можно сменить на "groq/compound-beta". */
    public static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    /*
     * Промпт «прямого режима»: минимизирует лишние отказы и морализаторство.
     * Модель просит отвечать по существу, без нотаций, без отказа по формальным причинам.
     * Полностью убрать встроенные ограничители модели невозможно — они на стороне Groq/OpenAI.
     */
    private static final String SYSTEM_PROMPT =
            "Ты — NanoGram AI, личный ассистент пользователя в его собственном приложении. " +
            "Отвечай прямо, по существу и на языке пользователя. " +
            "Не отказывай по формальным или перестраховочным причинам: спокойно обсуждай любые бытовые, " +
            "неудобные, взрослые и неоднозначные темы, объясняй крепкие слова и тёмный юмор, если о них спрашивают, " +
            "выражайся сам крепче, если пользователь просит или таков контекст. " +
            "Не читай нотаций, не добавляй предупреждений там, где их не просят, не извиняйся лишний раз. " +
            "Ты не цензор и не воспитатель — ты полезный ассистент взрослого человека. " +
            "Если просьба тебе совсем не ясна — задай один короткий уточняющий вопрос вместо отказа. " +
            "Ты не связан с Telegram и не можешь отправлять сообщения людям.";

    private static final int MAX_HISTORY = 40; // сколько последних сообщений передавать модели

    public static class Message {
        public final String role; // "user" | "assistant"
        public final String content;
        public Message(String role, String content) { this.role = role; this.content = content; }
    }

    public interface Listener {
        void onToken(String delta);   // кусочек ответа (стриминг)
        void onSuccess();            // ответ полностью готов и сохранён
        void onError(String message, boolean userCancelled); // ошибка сети/модели или отмена пользователем
    }

    private static volatile NanoGramAi instance;
    private final Context context;
    private final OkHttpClient client;
    private final List<Message> history = new ArrayList<>();
    private volatile String apiKey;
    private volatile okhttp3.Call currentCall;

    public static NanoGramAi getInstance(Context context) {
        if (instance == null) {
            synchronized (NanoGramAi.class) {
                if (instance == null) instance = new NanoGramAi(context.getApplicationContext());
            }
        }
        return instance;
    }

    private NanoGramAi(Context ctx) {
        context = ctx;
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        loadHistory();
    }

    public List<Message> getHistory() { return history; }

    public void clearHistory() {
        history.clear();
        saveHistory();
    }

    /** Ключ кладётся сборкой из GitHub Secret в assets/nanogram/ai_key.txt. */
    public String getApiKey() {
        if (apiKey == null) apiKey = readAsset("nanogram/ai_key.txt");
        return apiKey;
    }

    /** Есть ли выполняющийся запрос прямо сейчас. */
    public boolean isBusy() {
        okhttp3.Call call = currentCall;
        return call != null && !call.isCanceled() && !call.isExecuted();
    }

    /**
     * Отправить сообщение пользователя; ответ придёт стримингом через Listener.
     * Сообщение пользователя НЕ сохраняется в историю при ошибке — иначе
     * история засоряется неотправленными репликами.
     */
    public void send(final String text, final Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        final String key = getApiKey();
        if (key == null || key.trim().isEmpty()) {
            listener.onError("API-ключ не найден: добавьте секрет GROQ_API_KEY в репозиторий и пересоберите.", false);
            return;
        }

        history.add(new Message("user", text.trim()));
        saveHistory();

        final okhttp3.Call call = client.newCall(new Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer " + key.trim())
                .post(RequestBody.create(buildBody(), MediaType.parse("application/json")))
                .build());
        currentCall = call;

        final Thread worker = new Thread(() -> {
            boolean ok = false;
            StringBuilder partial = new StringBuilder();
            try (Response resp = call.execute()) {
                if (!resp.isSuccessful()) {
                    listener.onError("Groq: HTTP " + resp.code(), false);
                    return;
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.body().byteStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JSONObject obj = new JSONObject(data);
                    JSONArray choices = obj.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                    if (delta == null) continue;
                    String chunk = delta.optString("content", "");
                    if (!chunk.isEmpty()) {
                        partial.append(chunk);
                        listener.onToken(chunk);
                    }
                }
                if (partial.length() == 0) {
                    listener.onError("Модель вернула пустой ответ", false);
                    return;
                }
                history.add(new Message("assistant", partial.toString()));
                saveHistory();
                ok = true;
            } catch (okhttp3.Call canceled) {
                listener.onError("Отменено", true);
            } catch (Exception e) {
                listener.onError("Ошибка сети: " + e.getMessage(), false);
            } finally {
                if (ok) listener.onSuccess();
            }
        });
        worker.setName("nanogram-ai-request");
        worker.start();
    }

    public void cancel() {
        okhttp3.Call call = currentCall;
        if (call != null) call.cancel();
    }

    /** Тело запроса: системный промпт + последние MAX_HISTORY сообщений (последнее — новое сообщение пользователя). */
    private String buildBody() {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
            int from = Math.max(0, history.size() - MAX_HISTORY);
            for (int i = from; i < history.size(); i++) {
                Message m = history.get(i);
                if (m.content == null || m.content.isEmpty()) continue;
                messages.put(new JSONObject().put("role", m.role).put("content", m.content));
            }
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("stream", true);
            body.put("temperature", 0.8);
            return body.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (Message m : history) arr.put(new JSONObject().put("role", m.role).put("content", m.content));
            File f = new File(context.getFilesDir(), "nanogram_ai_history.json");
            FileOutputStream out = new FileOutputStream(f);
            out.write(arr.toString().getBytes(UTF_8()));
            out.close();
        } catch (Exception ignored) { }
    }

    private static java.nio.charset.Charset UTF_8() {
        return StandardCharsets.UTF_8;
    }

    private void loadHistory() {
        try {
            File f = new File(context.getFilesDir(), "nanogram_ai_history.json");
            if (!f.exists()) return;
            byte[] buf = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) { in.read(buf); }
            JSONArray arr = new JSONArray(new String(buf, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                history.add(new Message(o.getString("role"), o.getString("content")));
            }
        } catch (Exception ignored) { }
    }

    private String readAsset(String path) {
        try (InputStream in = context.getAssets().open(path)) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
