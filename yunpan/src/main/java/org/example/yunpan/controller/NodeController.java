package org.example.yunpan.controller;

import org.example.yunpan.entity.Node;
import org.example.yunpan.config.SessionManager;
import org.example.yunpan.config.UserStore;
import org.example.yunpan.service.FileService;
import org.example.yunpan.service.NodeAuthService;
import org.example.yunpan.service.NodeRegistry;
import org.example.yunpan.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
public class NodeController {

    private final NodeRegistry nodeRegistry;
    private final NodeAuthService nodeAuthService;
    private final FileService fileService;
    private final JwtUtils jwtUtils;
    private final SessionManager sessionManager;
    private final UserStore userStore;

    public NodeController(NodeRegistry nodeRegistry, NodeAuthService nodeAuthService,
                          FileService fileService, JwtUtils jwtUtils,
                          SessionManager sessionManager, UserStore userStore) {
        this.nodeRegistry = nodeRegistry;
        this.nodeAuthService = nodeAuthService;
        this.fileService = fileService;
        this.jwtUtils = jwtUtils;
        this.sessionManager = sessionManager;
        this.userStore = userStore;
    }

    // ===== 存储节点自注册/心跳（节点 Token 鉴权） =====

    @PostMapping("/api/node/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        String id = (String) body.get("id");
        requireNodeToken(token, id);
        String name = (String) body.getOrDefault("name", id);
        int frpPort = body.get("frpPort") instanceof Number ? ((Number) body.get("frpPort")).intValue() : 18080;
        String owner = (String) body.get("owner"); // 个人节点绑定用户
        // 将 X-Node-Token 保存到数据库，使每个节点拥有独立鉴权凭据
        String nodeToken = (token != null && !token.isBlank()) ? token.trim() : null;
        nodeRegistry.register(id, name, frpPort, owner, nodeToken);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok"));
    }

    @PostMapping("/api/node/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        String id = (String) body.get("id");
        requireNodeToken(token, id);
        long deviceTotal = body.get("deviceTotal") instanceof Number ? ((Number) body.get("deviceTotal")).longValue() : 0;
        long deviceFree = body.get("deviceFree") instanceof Number ? ((Number) body.get("deviceFree")).longValue() : 0;
        long usedCapacity = body.get("usedCapacity") instanceof Number ? ((Number) body.get("usedCapacity")).longValue() : 0;
        nodeRegistry.heartbeat(id, deviceTotal, deviceFree, usedCapacity);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok"));
    }

    @PostMapping("/api/node/unbind")
    public ResponseEntity<Map<String, Object>> unbind(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        String id = stringValue(body.get("id"));
        requireNodeToken(token, id);
        nodeRegistry.unbind(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "node unbound"));
    }

    @PostMapping("/api/node/files")
    public ResponseEntity<Map<String, Object>> recordFile(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        String username = stringValue(body.get("username"));
        String filePath = stringValue(body.get("filePath"));
        String nodeId = stringValue(body.get("nodeId"));
        requireNodeToken(token, nodeId);
        long size = body.get("size") instanceof Number ? ((Number) body.get("size")).longValue() : 0;
        if (username.isBlank() || filePath.isBlank() || nodeId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "username/filePath/nodeId 不能为空"));
        }
        fileService.recordRemoteFile(username, filePath, nodeId, size);
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok"));
    }

    @PostMapping("/api/node/auth-user")
    public ResponseEntity<Map<String, Object>> authUser(
            @RequestHeader(value = "X-Node-Token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        String nodeId = stringValue(body.get("nodeId"));
        requireNodeToken(token, nodeId);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少用户认证信息");
        }
        try {
            Claims claims = jwtUtils.parseToken(authorization.substring(7));
            String email = claims.getSubject();
            String loginId = claims.get("loginId", String.class);
            var userOpt = userStore.findByEmail(email);
            if (userOpt.isEmpty() || !userOpt.get().getEnabled()
                    || (loginId != null && !sessionManager.isValid(email, loginId))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户认证失败");
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "ok",
                    "data", Map.of("email", email)));
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户 Token 无效");
        }
    }

    // ===== 管理员节点管理（需 JWT + ADMIN） =====

    @GetMapping("/api/admin/nodes")
    public ResponseEntity<List<Map<String, Object>>> listNodes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node n : nodeRegistry.getAllNodes()) {
            result.add(nodeView(n));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/app-bind/nodes")
    public ResponseEntity<List<Map<String, Object>>> listMyNodes() {
        String email = org.example.yunpan.config.UserContext.get();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Node n : nodeRegistry.getAllNodes()) {
            if (email != null && email.equals(n.getOwner())) {
                result.add(nodeView(n));
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/nodes/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id) {
        nodeRegistry.approve(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "节点已批准上线"));
    }

    @DeleteMapping("/api/admin/nodes/{id}")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable String id) {
        nodeRegistry.remove(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "节点已删除"));
    }

    private void requireNodeToken(String token, String nodeId) {
        if (!nodeAuthService.isValidForNode(token, nodeId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "节点认证失败");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Map<String, Object> nodeView(Node n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("name", n.getName());
        m.put("frpPort", n.getFrpPort());
        m.put("status", n.getStatus());
        m.put("owner", n.getOwner());
        m.put("deviceTotal", n.getDeviceTotal());
        m.put("deviceFree", n.getDeviceFree());
        m.put("quota", n.getQuota());
        m.put("usedCapacity", n.getUsedCapacity());
        m.put("lastHeartbeat", n.getLastHeartbeat());
        return m;
    }
}
