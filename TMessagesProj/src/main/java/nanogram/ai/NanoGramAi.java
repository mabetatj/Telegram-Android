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
 * NanoGram AI - local AI chat engine backed by the Groq API.
 *
 * Self-contained module: no dependencies on Telegram fork classes, so adding
 * it cannot break the build.
 *
 * - The API key is read from assets/nanogram/ai_key.txt (placed there by the
 *   build from the GitHub Secret GROQ_API_KEY).
 * - History is stored LOCALLY (a file in the app's private folder); only the
 *   request itself goes to api.groq.com - like any AI client.
 * - Replies arrive as a stream; the onToken callback fires as text is generated.
 *
 * IMPORTANT for UI: all Listener callbacks come from a background thread -
 * update the interface via Activity.runOnUiThread(...).
 *
 * All user-visible strings are plain English ASCII so no encoding issue can garble them.
 */
public class NanoGramAi {

    /** Model on Groq. */
    public static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    /*
     * Assistant persona: helpful, direct, replies in the user's language.
     * Plain English ASCII prompt - safe against encoding corruption.
     */
    private static final String SYSTEM_PROMPT =
            "You are NanoGram AI, the user's personal assistant inside their own app. " +
            "Answer directly and to the point, in the language the user writes in. " +
            "Do not refuse over formal or overly cautious reasons: calmly discuss everyday, " +
            "awkward, adult and ambiguous topics, explain strong words and dark humor when asked, " +
            "and speak more bluntly yourself if the user requests it or the context calls for it. " +
            "Do not add notes, do not add warnings where none are requested, do not over-apologize. " +
            "You are not a censor and not a moralizer - you are a useful assistant for an adult. " +
            "If a request is truly unclear, ask one short clarifying question instead of refusing. " +
            "You are not related to Telegram and cannot send messages to people.";

    private static final int MAX_HISTORY = 40; // how many recent messages to send to the model

    public static class Message {
        public final String role; // "user" | "assistant"
        public final String content;
        public Message(String role, String content) { this.role = role; this.content = content; }
    }

    public interface Listener {
        void onToken(String delta);   // a piece of the streamed reply
        void onSuccess();             // the reply is complete and saved
        void onError(String message); // network or model error
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

    /** The key is placed by the build from the GitHub Secret into assets/nanogram/ai_key.txt. */
    public String getApiKey() {
        if (apiKey == null) apiKey = readAsset("nanogram/ai_key.txt");
        return apiKey;
    }

    /** Send a user message; the reply streams back through the Listener. */
    public void send(String text, final Listener listener) {
        if (text == null || text.trim().isEmpty()) return;
        final String key = getApiKey();
        if (key == null || key.trim().isEmpty()) {
            listener.onError("API key is missing. Add the GROQ_API_KEY secret to the repository and rebuild.");
            return;
        }
        final String userText = text.trim();
        history.add(new Message("user", userText));
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
                    removeLastUserMessage();
                    listener.onError(describeHttpError(resp.code()));
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
                    removeLastUserMessage();
                    listener.onError("The model returned an empty reply. Try again.");
                    return;
                }
                history.add(new Message("assistant", partial.toString()));
                saveHistory();
                ok = true;
            } catch (Exception e) {
                removeLastUserMessage();
                listener.onError("Network error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            } finally {
                if (ok) listener.onSuccess();
            }
        }).start();
    }

    /** Human-readable names for common HTTP codes instead of raw numbers. */
    private static String describeHttpError(int code) {
        switch (code) {
            case 401: return "Invalid API key (401). Check the GROQ_API_KEY secret.";
            case 403: return "Access denied by Groq (403). Check the API key limits.";
            case 404: return "Model not found (404). The model ID may be deprecated.";
            case 413: return "The conversation is too long (413). Clear the history and retry.";
            case 429: return "Rate limit reached (429). Wait a minute and try again.";
            case 503: return "Groq service is temporarily unavailable (503). Try again later.";
            case 529: return "Groq is overloaded (529). Try again later.";
            default: return "Groq returned HTTP " + code;
        }
    }

    /** Drops the message we just added if the request failed, so history stays clean. */
    private synchronized void removeLastUserMessage() {
        if (!history.isEmpty() && "user".equals(history.get(history.size() - 1).role)) {
            history.remove(history.size() - 1);
            saveHistory();
        }
    }

    public void cancel() {
        okhttp3.Call call = currentCall;
        if (call != null) call.cancel();
    }

    /** Request body: system prompt + the last MAX_HISTORY messages (the last one is the new user message). */
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
