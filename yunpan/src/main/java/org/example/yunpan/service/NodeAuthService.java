package org.example.yunpan.service;

import org.example.yunpan.entity.Node;
import org.example.yunpan.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class NodeAuthService {

    private final String nodeToken;
    private final NodeMapper nodeMapper;

    @Autowired
    public NodeAuthService(@Value("${yunpan.node.token}") String nodeToken, NodeMapper nodeMapper) {
        this.nodeToken = nodeToken == null ? "" : nodeToken.trim();
        this.nodeMapper = nodeMapper;
    }

    public NodeAuthService(@Value("${yunpan.node.token}") String nodeToken) {
        this.nodeToken = nodeToken == null ? "" : nodeToken.trim();
        this.nodeMapper = null;
    }

    public boolean isValid(String token) {
        return constantTimeEquals(nodeToken, token);
    }

    public boolean isValidForNode(String token, String nodeId) {
        if (isValid(token)) return true;
        if (token == null || token.isBlank() || nodeId == null || nodeId.isBlank() || nodeMapper == null) {
            return false;
        }
        Node node = nodeMapper.selectById(nodeId.trim());
        return node != null && constantTimeEquals(node.getNodeToken(), token);
    }

    private boolean constantTimeEquals(String expectedValue, String actualValue) {
        if (expectedValue == null || expectedValue.isBlank()
                || actualValue == null || actualValue.isBlank()) {
            return false;
        }
        byte[] expected = expectedValue.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualValue.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
