package io.turboio.enhanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Android implementation of Turbo-IO codex-bridge protocol v1. */
final class CodexBridgeClient {
    private static final String PREFS = "turbo_io_codex_v1";
    private static final String KEY_ALIAS = "turbo_io_codex_token_v1";
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{32,256}$");
    private static final int MAX_RESPONSE = 2 * 1024 * 1024;

    private final SharedPreferences prefs;

    CodexBridgeClient(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String endpoint() { return prefs.getString("endpoint", ""); }
    String taskId() { return prefs.getString("taskId", ""); }
    boolean hasUnknownDelivery() { return prefs.contains("pending"); }

    void save(String endpoint, String token) throws Exception {
        String normalized = normalize(endpoint);
        if (!TOKEN.matcher(token).matches()) throw new IllegalArgumentException("令牌必须是 32-256 位字母、数字、_ 或 -");
        prefs.edit().putString("endpoint", normalized).putString("token", encrypt(token, normalized)).apply();
    }

    String refresh() throws Exception {
        JSONObject state = call("/v1/state", null);
        if (state.optInt("protocolVersion", -1) != 1) throw new IllegalStateException("bridge 协议版本不匹配");
        JSONArray tasks = state.optJSONArray("tasks");
        if (tasks == null || tasks.length() > 16) throw new IllegalStateException("bridge tasks 响应无效");
        String selected = taskId();
        JSONObject task = null;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject candidate = tasks.getJSONObject(i);
            if (selected.equals(candidate.optString("id"))) { task = candidate; break; }
        }
        if (task == null && tasks.length() > 0) {
            task = tasks.getJSONObject(0);
            prefs.edit().putString("taskId", task.optString("id")).apply();
        }
        StringBuilder output = new StringBuilder();
        output.append(state.optBoolean("online") ? "电脑桥接在线" : "Codex 进程离线");
        output.append(state.optBoolean("readOnly", true) ? " · 只读" : " · 工作区可写");
        output.append("\nworkspace: ").append(state.optString("workspace", ""));
        if (task != null) {
            output.append("\ntask: ").append(task.optString("id"));
            output.append("\nstatus: ").append(task.optString("status"));
            String answer = task.optString("answer", "");
            if (answer.length() > 4000) answer = answer.substring(answer.length() - 4000);
            if (!answer.isEmpty()) output.append("\nanswer:\n").append(answer);
            JSONArray pending = task.optJSONArray("pending");
            if (pending != null && pending.length() > 0) output.append("\n待手机确认: ").append(pending.length()).append(" 项");
        }
        return output.toString();
    }

    String message(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.getBytes(StandardCharsets.UTF_8).length > 8192) {
            throw new IllegalArgumentException("任务文本不能为空且不能超过 8192 bytes");
        }
        if (hasUnknownDelivery()) throw new IllegalStateException("上次提交结果未知，请先刷新核对，避免重复任务");
        JSONObject body = new JSONObject().put("text", trimmed).put("requestId", UUID.randomUUID().toString());
        if (!taskId().isEmpty()) body.put("taskId", taskId());
        return mutate("/v1/message", body);
    }

    String stop() throws Exception {
        if (taskId().isEmpty()) throw new IllegalStateException("当前没有选中的 Codex 任务");
        JSONObject body = new JSONObject().put("taskId", taskId()).put("requestId", UUID.randomUUID().toString());
        return mutate("/v1/stop", body);
    }

    void clearUnknownAfterManualCheck() { prefs.edit().remove("pending").apply(); }

    private String mutate(String path, JSONObject body) throws Exception {
        prefs.edit().putString("pending", new JSONObject().put("path", path).put("body", body).toString()).commit();
        try {
            JSONObject result = call(path, body);
            if (!result.optBoolean("accepted") || result.optString("taskId").isEmpty()) {
                throw new IllegalStateException("bridge 未确认接收请求");
            }
            String id = result.getString("taskId");
            prefs.edit().putString("taskId", id).remove("pending").commit();
            return "bridge 已接受，taskId=" + id;
        } catch (Exception error) {
            throw new IllegalStateException("提交结果未知；不会自动重发。" + error.getMessage(), error);
        }
    }

    private JSONObject call(String path, JSONObject body) throws Exception {
        String endpoint = endpoint();
        if (endpoint.isEmpty()) throw new IllegalStateException("请先保存桥接配置");
        String encrypted = prefs.getString("token", "");
        if (encrypted.isEmpty()) throw new IllegalStateException("没有已保存令牌");
        String token = decrypt(encrypted, endpoint);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(40000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        if (body == null) {
            connection.setRequestMethod("GET");
        } else {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
        }
        int status = connection.getResponseCode();
        InputStream stream = status == 200 ? connection.getInputStream() : connection.getErrorStream();
        byte[] data = readBounded(stream);
        connection.disconnect();
        JSONObject object = data.length == 0 ? new JSONObject() : new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (status != 200) throw new IllegalStateException(object.optString("error", "http_" + status));
        return object;
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; int total = 0;
            while ((count = in.read(buffer)) != -1) {
                total += count; if (total > MAX_RESPONSE) throw new IllegalStateException("bridge 响应超过 2 MiB");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static String normalize(String input) throws Exception {
        URI uri = new URI(input == null ? "" : input.trim());
        String scheme = uri.getScheme(); String host = uri.getHost();
        boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(scheme) || ("http".equalsIgnoreCase(scheme) && loopback))
                || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("请输入 HTTPS 地址；ADB reverse 调试可用 http://127.0.0.1:端口");
        }
        return new URI(scheme.toLowerCase(), null, host.toLowerCase(), uri.getPort(), null, null, null).toString();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static String encrypt(String value, String endpoint) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(endpoint.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] joined = new byte[cipher.getIV().length + encrypted.length];
        System.arraycopy(cipher.getIV(), 0, joined, 0, cipher.getIV().length);
        System.arraycopy(encrypted, 0, joined, cipher.getIV().length, encrypted.length);
        return Base64.encodeToString(joined, Base64.NO_WRAP);
    }

    private static String decrypt(String encoded, String endpoint) throws Exception {
        byte[] joined = Base64.decode(encoded, Base64.NO_WRAP);
        if (joined.length < 13) throw new IllegalStateException("令牌密文损坏");
        byte[] iv = new byte[12]; byte[] encrypted = new byte[joined.length - 12];
        System.arraycopy(joined, 0, iv, 0, 12); System.arraycopy(joined, 12, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(endpoint.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
