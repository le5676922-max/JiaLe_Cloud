package org.example.yunpan.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "yunpan.coordinator", havingValue = "false")
public class NodeBootstrap {

    @Value("${yunpan.node.id}")
    private String nodeId;

    @Value("${yunpan.node.name:}")
    private String nodeName;

    @Value("${yunpan.node.frp-port}")
    private int frpPort;

    @Value("${yunpan.cloud.host}")
    private String cloudHost;

    @Value("${yunpan.node.token}")
    private String nodeToken;

    @PostConstruct
    public void init() {
        String name = (nodeName == null || nodeName.isBlank()) ? nodeId : nodeName;
        String safeToken = nodeToken == null ? "" : nodeToken.trim();
        try {
            // nodeToken 随请求体发送，由 NodeRegistry.register() 保存到数据库
            String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"frpPort\":%d,\"nodeToken\":\"%s\"}",
                    nodeId, name, frpPort, safeToken);
            URI uri = new URI("http://" + cloudHost + "/api/node/register");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Node-Token", safeToken);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            System.out.println("Node register: " + code + " " +
                    new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Node register failed: " + e.getMessage());
        }
    }
}
