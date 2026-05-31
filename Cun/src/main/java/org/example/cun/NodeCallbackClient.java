package org.example.cun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class NodeCallbackClient {

    private final String cloudHost;
    private final String nodeId;
    private final String nodeToken;

    public NodeCallbackClient(@Value("${yunpan.cloud.host}") String cloudHost,
                              @Value("${yunpan.node.id}") String nodeId,
                              @Value("${yunpan.node.token}") String nodeToken) {
        this.cloudHost = cloudHost;
        this.nodeId = nodeId;
        this.nodeToken = nodeToken;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void recordFile(String username, String filePath, long size) {
        try {
            String json = String.format(
                    "{\"username\":\"%s\",\"filePath\":\"%s\",\"nodeId\":\"%s\",\"size\":%d}",
                    escape(username), escape(filePath), escape(nodeId), size);
            URI uri = new URI("http://" + cloudHost + "/api/node/files");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Node-Token", nodeToken);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("云端文件索引回调失败: " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
