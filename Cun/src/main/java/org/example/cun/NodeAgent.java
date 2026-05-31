package org.example.cun;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Component
public class NodeAgent {
    @Value("${yunpan.node.id:auto}") private String nodeId;
    @Value("${yunpan.node.frp-port:18082}") private int frpPort;
    @Value("${yunpan.node.owner:}") private String owner;
    @Value("${yunpan.cloud.host}") private String cloudHost;
    @Value("${yunpan.storage.root-path:./storage_data}") private String storageRoot;
    @Value("${yunpan.node.token}") private String nodeToken;
    private long lastCalc; private long cachedUsed;

    @PostConstruct
    public void register() {
        if("auto".equals(nodeId)) nodeId = "node-" + System.currentTimeMillis() % 100000;
        try {
            String own = (owner != null && !owner.isBlank()) ? String.format(", \"owner\":\"%s\"", owner) : "";
            // 将 nodeToken 随请求体发送，由 NodeRegistry.register() 保存到数据库
            String safeToken = nodeToken == null ? "" : nodeToken;
            String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"frpPort\":%d,\"nodeToken\":\"%s\"%s}",
                    nodeId, nodeId, frpPort, safeToken, own);
            post("/api/node/register", json);
        } catch(Exception ignored) {}
    }

    @Scheduled(fixedRate = 30000)
    public void heartbeat() {
        try {
            Path root = Path.of(storageRoot);
            if(!Files.exists(root)) Files.createDirectories(root);
            java.nio.file.FileStore fs = Files.getFileStore(root);
            long now = System.currentTimeMillis();
            if(now - lastCalc > 300_000) { cachedUsed = calcUsed(root); lastCalc = now; }
            String json = String.format("{\"id\":\"%s\",\"deviceTotal\":%d,\"deviceFree\":%d,\"usedCapacity\":%d}",
                    nodeId, fs.getTotalSpace(), fs.getUsableSpace(), cachedUsed);
            post("/api/node/heartbeat", json);
        } catch(Exception ignored) {}
    }

    private void post(String path, String json) throws Exception {
        URI uri = new URI("http://" + cloudHost + path);
        HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(5000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Node-Token", nodeToken);
        try(OutputStream o = c.getOutputStream()) { o.write(json.getBytes(StandardCharsets.UTF_8)); }
        c.getResponseCode(); c.disconnect();
    }

    private long calcUsed(Path root) {
        try(var w = Files.walk(root)) {
            return w.filter(Files::isRegularFile).mapToLong(p->{try{return Files.size(p);}catch(Exception e){return 0L;}}).sum();
        } catch(Exception e) { return 0L; }
    }
}
