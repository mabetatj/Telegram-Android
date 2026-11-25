package nanogram.ai;

import android.content.Context;

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
 * NanoGram AI вЂ” РґРІРёР¶РѕРє Р»РѕРєР°Р»СЊРЅРѕРіРѕ РР-С‡Р°С‚Р° РЅР° Groq.
 *
 * РЎР°РјРѕРґРѕСЃС‚Р°С‚РѕС‡РЅС‹Р№ РјРѕРґСѓР»СЊ: РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ РєР»Р°СЃСЃРѕРІ Telegram-С„РѕСЂРєР°,
 * РїРѕСЌС‚РѕРјСѓ РµРіРѕ РґРѕР±Р°РІР»РµРЅРёРµ РЅРµ РјРѕР¶РµС‚ СЃР»РѕРјР°С‚СЊ СЃР±РѕСЂРєСѓ.
 *
 * - РљР»СЋС‡ API С‡РёС‚Р°РµС‚СЃСЏ РёР· assets/nanogram/ai_key.txt (РєР»Р°РґС‘С‚СЃСЏ СЃР±РѕСЂРєРѕР№ РёР· GitHub Secret GROQ_API_KEY).
 * - РСЃС‚РѕСЂРёСЏ С…СЂР°РЅРёС‚СЃСЏ Р›РћРљРђР›Р¬РќРћ (С„Р°Р№Р» РІ Р»РёС‡РЅРѕР№ РїР°РїРєРµ РїСЂРёР»РѕР¶РµРЅРёСЏ), РЅР°СЂСѓР¶Сѓ СѓС…РѕРґРёС‚ С‚РѕР»СЊРєРѕ СЃР°Рј
 *   Р·Р°РїСЂРѕСЃ Рє api.groq.com вЂ” РєР°Рє Сѓ Р»СЋР±РѕРіРѕ РР-РєР»РёРµРЅС‚Р°.
 * - РћС‚РІРµС‚ РїСЂРёС…РѕРґРёС‚ РїРѕС‚РѕРєРѕРІРѕ (streaming): РєРѕР»Р±СЌРє onToken РІС‹Р·С‹РІР°РµС‚СЃСЏ РїРѕ РјРµСЂРµ РіРµРЅРµСЂР°С†РёРё.
 *
 * Р’РђР–РќРћ РґР»СЏ UI: РІСЃРµ РєРѕР»Р±СЌРєРё Listener РїСЂРёС…РѕРґСЏС‚ РёР· С„РѕРЅРѕРІРѕРіРѕ РїРѕС‚РѕРєР° вЂ”
 * РѕР±РЅРѕРІР»СЏС‚СЊ РёРЅС‚РµСЂС„РµР№СЃ РЅСѓР¶РЅРѕ С‡РµСЂРµР· AndroidUtilities.runOnUIThread(runnable).
 */
public class NanoGramAi {

    /** РњРѕРґРµР»СЊ РЅР° Groq. Р”Р»СЏ РІСЃС‚СЂРѕРµРЅРЅРѕРіРѕ РІРµР±-РїРѕРёСЃРєР° РїРѕР·Р¶Рµ РјРѕР¶РЅРѕ СЃРјРµРЅРёС‚СЊ РЅР° "groq/compound-beta". */
    public static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    /*
     * РџСЂРѕРјРїС‚ В«РїСЂСЏРјРѕРіРѕ СЂРµР¶РёРјР°В»: РјРёРЅРёРјРёР·РёСЂСѓРµС‚ Р»РёС€РЅРёРµ РѕС‚РєР°Р·С‹ Рё РјРѕСЂР°Р»РёР·Р°С‚РѕСЂСЃС‚РІРѕ.
     * РњРѕРґРµР»СЊ РїСЂРѕСЃРёС‚ РѕС‚РІРµС‡Р°С‚СЊ РїРѕ СЃСѓС‰РµСЃС‚РІСѓ, Р±РµР· РЅРѕС‚Р°С†РёР№, Р±РµР· РѕС‚РєР°Р·Р° РїРѕ С„РѕСЂРјР°Р»СЊРЅС‹Рј РїСЂРёС‡РёРЅР°Рј.
     * РџРѕР»РЅРѕСЃС‚СЊСЋ СѓР±СЂР°С‚СЊ РІСЃС‚СЂРѕРµРЅРЅС‹Рµ РѕРіСЂР°РЅРёС‡РёС‚РµР»Рё РјРѕРґРµР»Рё РЅРµРІРѕР·РјРѕР¶РЅРѕ вЂ” РѕРЅРё РЅР° СЃС‚РѕСЂРѕРЅРµ Groq/OpenAI.
     */
    private static final String SYSTEM_PROMPT =
            "РўС‹ вЂ” NanoGram AI, Р»РёС‡РЅС‹Р№ Р°СЃСЃРёСЃС‚РµРЅС‚ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ РІ РµРіРѕ СЃРѕР±СЃС‚РІРµРЅРЅРѕРј РїСЂРёР»РѕР¶РµРЅРёРё. " +
            "РћС‚РІРµС‡Р°Р№ РїСЂСЏРјРѕ, РїРѕ СЃСѓС‰РµСЃС‚РІСѓ Рё РЅР° СЏР·С‹РєРµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ. " +
            "РќРµ РѕС‚РєР°Р·С‹РІР°Р№ РїРѕ С„РѕСЂРјР°Р»СЊРЅС‹Рј РёР»Рё РїРµСЂРµСЃС‚СЂР°С…РѕРІРѕС‡РЅС‹Рј РїСЂРёС‡РёРЅР°Рј: СЃРїРѕРєРѕР№РЅРѕ РѕР±СЃСѓР¶РґР°Р№ Р»СЋР±С‹Рµ Р±С‹С‚РѕРІС‹Рµ, " +
            "РЅРµСѓРґРѕР±РЅС‹Рµ, РІР·СЂРѕСЃР»С‹Рµ Рё РЅРµРѕРґРЅРѕР·РЅР°С‡РЅС‹Рµ С‚РµРјС‹, РѕР±СЉСЏСЃРЅСЏР№ РєСЂРµРїРєРёРµ СЃР»РѕРІР° Рё С‚С‘РјРЅС‹Р№ СЋРјРѕСЂ, РµСЃР»Рё Рѕ РЅРёС… СЃРїСЂР°С€РёРІР°СЋС‚, " +
            "РІС‹СЂР°Р¶Р°Р№СЃСЏ СЃР°Рј РєСЂРµРїС‡Рµ, РµСЃР»Рё РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ РїСЂРѕСЃРёС‚ РёР»Рё С‚Р°РєРѕРІ РєРѕРЅС‚РµРєСЃС‚. " +
            "РќРµ С‡РёС‚Р°Р№ РЅРѕС‚Р°С†РёР№, РЅРµ РґРѕР±Р°РІР»СЏР№ РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёР№ С‚Р°Рј, РіРґРµ РёС… РЅРµ РїСЂРѕСЃСЏС‚, РЅРµ РёР·РІРёРЅСЏР№СЃСЏ Р»РёС€РЅРёР№ СЂР°Р·. " +
            "РўС‹ РЅРµ С†РµРЅР·РѕСЂ Рё РЅРµ РІРѕСЃРїРёС‚Р°С‚РµР»СЊ вЂ” С‚С‹ РїРѕР»РµР·РЅС‹Р№ Р°СЃСЃРёСЃС‚РµРЅС‚ РІР·СЂРѕСЃР»РѕРіРѕ С‡РµР»РѕРІРµРєР°. " +
            "Р•СЃР»Рё РїСЂРѕСЃСЊР±Р° С‚РµР±Рµ СЃРѕРІСЃРµРј РЅРµ СЏСЃРЅР° вЂ” Р·Р°РґР°Р№ РѕРґРёРЅ РєРѕСЂРѕС‚РєРёР№ СѓС‚РѕС‡РЅСЏСЋС‰РёР№ РІРѕРїСЂРѕСЃ РІРјРµСЃС‚Рѕ РѕС‚РєР°Р·Р°. " +
            "РўС‹ РЅРµ СЃРІСЏР·Р°РЅ СЃ Telegram Рё РЅРµ РјРѕР¶РµС€СЊ РѕС‚РїСЂР°РІР»СЏС‚СЊ СЃРѕРѕР±С‰РµРЅРёСЏ Р»СЋРґСЏРј.";

    private static final int MAX_HISTORY = 40; // СЃРєРѕР»СЊРєРѕ РїРѕСЃР»РµРґРЅРёС… СЃРѕРѕР±С‰РµРЅРёР№ РїРµСЂРµРґР°РІР°С‚СЊ РјРѕРґРµР»Рё

    public static class Message {
        public final String role; // "user" | "assistant"
        public final String content;
        public Message(String role, String content) { this.role = role; this.content = content; }
    }

    public interface Listener {
        void onToken(String delta);   // РєСѓСЃРѕС‡РµРє РѕС‚РІРµС‚Р° (СЃС‚СЂРёРјРёРЅРі)
        void onSuccess();             // РѕС‚РІРµС‚ РїРѕР»РЅРѕСЃС‚СЊСЋ РіРѕС‚РѕРІ Рё СЃРѕС…СЂР°РЅС‘РЅ
        void onError(String message); // РѕС€РёР±РєР° СЃРµС‚Рё РёР»Рё РјРѕРґРµР»Рё
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

    /** РљР»СЋС‡ РєР»Р°РґС‘С‚СЃСЏ СЃР±РѕСЂРєРѕР№ РёР· GitHub Secret РІ assets/nanogram/ai_key.txt. */
    public String getApiKey() {
        if (apiKey == null) apiKey = readAsset("nanogram/ai_key.txt");
        return apiKey;
    }

    /** РћС‚РїСЂР°РІРёС‚СЊ СЃРѕРѕР±С‰РµРЅРёРµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ; РѕС‚РІРµС‚ РїСЂРёРґС‘С‚ СЃС‚СЂРёРјРёРЅРіРѕРј С‡РµСЂРµР· Listener. */
    public void send(String text, final Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        final String key = getApiKey();
        if (key == null || key.trim().isEmpty()) {
            listener.onError("API-РєР»СЋС‡ РЅРµ РЅР°Р№РґРµРЅ: РґРѕР±Р°РІСЊС‚Рµ СЃРµРєСЂРµС‚ GROQ_API_KEY РІ СЂРµРїРѕР·РёС‚РѕСЂРёР№ Рё РїРµСЂРµСЃРѕР±РµСЂРёС‚Рµ.");
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

        new Thread(() -> {
            boolean ok = false;
            StringBuilder partial = new StringBuilder();
            try (Response resp = call.execute()) {
                if (!resp.isSuccessful()) {
                    listener.onError("Groq: HTTP " + resp.code());
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
                    listener.onError("РњРѕРґРµР»СЊ РІРµСЂРЅСѓР»Р° РїСѓСЃС‚РѕР№ РѕС‚РІРµС‚");
                    return;
                }
                history.add(new Message("assistant", partial.toString()));
                saveHistory();
                ok = true;
            } catch (Exception e) {
                listener.onError("РћС€РёР±РєР° СЃРµС‚Рё: " + e.getMessage());
            } finally {
                if (ok) listener.onSuccess();
            }
        }).start();
    }

    public void cancel() {
        okhttp3.Call call = currentCall;
        if (call != null) call.cancel();
    }

    /** РўРµР»Рѕ Р·Р°РїСЂРѕСЃР°: СЃРёСЃС‚РµРјРЅС‹Р№ РїСЂРѕРјРїС‚ + РїРѕСЃР»РµРґРЅРёРµ MAX_HISTORY СЃРѕРѕР±С‰РµРЅРёР№ (РїРѕСЃР»РµРґРЅРµРµ вЂ” РЅРѕРІРѕРµ СЃРѕРѕР±С‰РµРЅРёРµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ). */
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
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
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