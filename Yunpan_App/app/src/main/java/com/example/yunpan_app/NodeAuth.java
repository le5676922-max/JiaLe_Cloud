package com.example.yunpan_app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class NodeAuth {
    private final NodeConfig config;
    private final CloudClient cloud;

    public NodeAuth(NodeConfig config, CloudClient cloud) {
        this.config = config;
        this.cloud = cloud;
    }

    public boolean isNodeToken(String token) {
        if (token == null || token.isBlank()) return false;
        return MessageDigest.isEqual(config.nodeToken.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8));
    }

    public String requireUser(String authorization) throws Exception {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("missing bearer token");
        }
        return cloud.verifyUser(authorization);
    }
}
