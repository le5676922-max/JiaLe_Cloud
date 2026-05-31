package com.example.yunpan_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AppBindClient {
    private final Context context;

    public AppBindClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void bind(String ticketOrDeepLink) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("yunpan", Context.MODE_PRIVATE);
        // 若设备之前已绑定过节点，将旧 nodeId 随请求发送，便于后端清理孤儿节点
        String existingNodeId = prefs.getString("node_id", "");

        JSONObject req = new JSONObject();
        req.put("ticket", ticketOrDeepLink == null ? "" : ticketOrDeepLink.trim());
        if (existingNodeId != null && !existingNodeId.isBlank()) {
            req.put("existingNodeId", existingNodeId.trim());
        }

        URL url = new URL(context.getString(R.string.bind_base_url).replaceAll("/+$", "")
                + "/api/app-bind/consume");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) {
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = StreamUtils.readUtf8(stream);
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(body.isBlank() ? "绑定失败" : body);
        }

        JSONObject root = new JSONObject(body);
        JSONObject data = root.getJSONObject("data");
        prefs.edit()
                .putString("email", data.optString("email", ""))
                .putString("node_id", data.getString("nodeId"))
                .putString("node_token", data.getString("nodeToken"))
                .putString("api_base", data.getString("apiBase"))
                .putString("frp_host", data.getString("frpHost"))
                .putInt("remote_port", data.getInt("remotePort"))
                .putInt("local_port", 8080)
                .remove("host")
                .remove("jwt_secret")
                .remove("master_key")
                .apply();
    }

    public void unbind() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("yunpan", Context.MODE_PRIVATE);
        String apiBase = prefs.getString("api_base", "");
        String nodeId = prefs.getString("node_id", "");
        String nodeToken = prefs.getString("node_token", "");
        if (apiBase.isBlank() || nodeId.isBlank() || nodeToken.isBlank()) {
            return;
        }

        JSONObject req = new JSONObject();
        req.put("id", nodeId);
        URL url = new URL(apiBase.replaceAll("/+$", "") + "/api/node/unbind");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Node-Token", nodeToken);
        try (OutputStream out = c.getOutputStream()) {
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String body = StreamUtils.readUtf8(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(body.isBlank() ? "解绑失败" : body);
        }
    }
}
