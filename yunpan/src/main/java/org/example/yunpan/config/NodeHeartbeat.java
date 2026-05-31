package org.example.yunpan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "yunpan.coordinator", havingValue = "false")
public class NodeHeartbeat {

    @Value("${yunpan.node.id}")
    private String nodeId;

    @Value("${yunpan.cloud.host}")
    private String cloudHost;

    @Value("${yunpan.storage.root-path:./storage_data}")
    private String storageRootPath;

    @Value("${yunpan.node.token}")
    private String nodeToken;

    private long lastCalcTime = 0;
    private long cachedUsed = 0;

    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        try {
            Path storagePath = Path.of(storageRootPath);
            if (!Files.exists(storagePath)) Files.createDirectories(storagePath);

            java.nio.file.FileStore fs = Files.getFileStore(storagePath);
            long deviceTotal = fs.getTotalSpace();
            long deviceFree = fs.getUsableSpace();

            long now = System.currentTimeMillis();
            if (now - lastCalcTime > 300_000) {
                cachedUsed = calcUsed(storagePath);
                lastCalcTime = now;
            }

            String json = String.format(
                    "{\"id\":\"%s\",\"deviceTotal\":%d,\"deviceFree\":%d,\"usedCapacity\":%d}",
                    nodeId, deviceTotal, deviceFree, cachedUsed);

            URI uri = new URI("http://" + cloudHost + "/api/node/heartbeat");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Node-Token", nodeToken);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private long calcUsed(Path root) {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> { try { return Files.size(p); } catch (Exception e) { return 0L; } })
                    .sum();
        } catch (Exception e) { return 0L; }
    }
}
