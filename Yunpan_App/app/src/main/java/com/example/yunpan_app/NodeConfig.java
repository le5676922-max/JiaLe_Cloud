package com.example.yunpan_app;

import android.content.Context;
import android.content.SharedPreferences;

public class NodeConfig {
    public final String apiBase;
    public final String frpHost;
    public final String email;
    public final String nodeId;
    public final int localPort;
    public final int remotePort;
    public final String nodeToken;

    private NodeConfig(String apiBase, String frpHost, String email, String nodeId,
                       int localPort, int remotePort, String nodeToken) {
        this.apiBase = apiBase;
        this.frpHost = frpHost;
        this.email = email;
        this.nodeId = nodeId;
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.nodeToken = nodeToken;
    }

    public static NodeConfig load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("yunpan", Context.MODE_PRIVATE);
        String nodeId = prefs.getString("node_id", "");
        if (nodeId == null || nodeId.isBlank()) {
            nodeId = "phone-" + (System.currentTimeMillis() % 100000);
            prefs.edit().putString("node_id", nodeId).apply();
        }
        return new NodeConfig(
                prefs.getString("api_base", prefs.getString("host", "")).trim(),
                prefs.getString("frp_host", "").trim(),
                prefs.getString("email", "").trim(),
                nodeId,
                prefs.getInt("local_port", 8080),
                prefs.getInt("remote_port", 18082),
                prefs.getString("node_token", "")
        );
    }

    public String apiUrl(String path) {
        String base = apiBase.replaceAll("/+$", "");
        return base + path;
    }

    public String normalizedFrpHost() {
        String host = frpHost == null || frpHost.isBlank() ? apiBase : frpHost;
        return host.replace("http://", "").replace("https://", "").replaceAll("/+$", "");
    }
}
