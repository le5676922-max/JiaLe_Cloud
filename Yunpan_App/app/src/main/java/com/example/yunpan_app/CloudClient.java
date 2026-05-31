package com.example.yunpan_app;

import android.os.StatFs;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class CloudClient {
    private final NodeConfig config;

    public CloudClient(NodeConfig config) {
        this.config = config;
    }

    public void register() {
        try {
            if (config.nodeToken.isBlank() || config.apiBase.isBlank()) return;
            JSONObject json = new JSONObject();
            json.put("id", config.nodeId);
            json.put("name", config.nodeId);
            json.put("frpPort", config.remotePort);
            json.put("owner", config.email);
            post("/api/node/register", json);
        } catch (Exception ignored) {}
    }

    public void heartbeat(StatFs stat, long usedBytes) {
        try {
            if (config.nodeToken.isBlank() || config.apiBase.isBlank()) return;
            JSONObject json = new JSONObject();
            json.put("id", config.nodeId);
            json.put("deviceTotal", stat.getTotalBytes());
            json.put("deviceFree", stat.getAvailableBytes());
            json.put("usedCapacity", usedBytes);
            post("/api/node/heartbeat", json);
        } catch (Exception ignored) {}
    }

    public void recordFile(String username, String filePath, long size) {
        try {
            if (config.nodeToken.isBlank() || config.apiBase.isBlank()) return;
            JSONObject json = new JSONObject();
            json.put("username", username);
            json.put("filePath", filePath);
            json.put("nodeId", config.nodeId);
            json.put("size", size);
            post("/api/node/files", json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String verifyUser(String authorization) throws Exception {
        JSONObject json = new JSONObject();
        json.put("nodeId", config.nodeId);
        HttpURLConnection c = openPost("/api/node/auth-user");
        c.setRequestProperty("Authorization", authorization == null ? "" : authorization);
        try (OutputStream out = c.getOutputStream()) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String body = StreamUtils.readUtf8(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) throw new SecurityException("user auth failed");
        return new JSONObject(body).getJSONObject("data").getString("email");
    }

    private void post(String path, JSONObject json) throws Exception {
        HttpURLConnection c = openPost(path);
        try (OutputStream out = c.getOutputStream()) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("cloud returned " + code);
    }

    private HttpURLConnection openPost(String path) throws Exception {
        URL url = new URL(config.apiUrl(path));
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Node-Token", config.nodeToken);
        return c;
    }
}
