package org.example.yunpan.controller;

import org.example.yunpan.config.UserContext;
import org.example.yunpan.service.AppBindService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * App 扫码绑定控制器。
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li>绑定码由管理员在 Web 管理后台创建（{@code POST /api/app-bind/tickets}）</li>
 *   <li>创建的节点 owner 为当前登录的管理员，所有节点作为共享基础设施服务于全体用户</li>
 *   <li>{@code /api/app-bind/consume} 不需要 JWT 认证 —— 绑定码本身就是一次性凭证（5 分钟有效）</li>
 *   <li>绑定码仅存储 SHA-256 哈希，明文仅创建时返回一次，丢失不可恢复</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/app-bind")
public class AppBindController {
    private final AppBindService appBindService;

    public AppBindController(AppBindService appBindService) {
        this.appBindService = appBindService;
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket() {
        AppBindService.BindTicket ticket = appBindService.createTicket(UserContext.get());
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "ok",
                "data", Map.of(
                        "ticket", ticket.ticket(),
                        "deepLink", ticket.deepLink(),
                        "expiresAt", ticket.expiresAt()
                )
        ));
    }

    @PostMapping("/consume")
    public ResponseEntity<Map<String, Object>> consume(@RequestBody Map<String, Object> body) {
        String ticket = body.get("ticket") == null ? "" : body.get("ticket").toString();
        String existingNodeId = body.get("existingNodeId") == null ? null : body.get("existingNodeId").toString();
        AppBindService.BindConfig config = appBindService.consumeTicket(ticket, existingNodeId);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "ok",
                "data", Map.of(
                        "nodeId", config.nodeId(),
                        "nodeToken", config.nodeToken(),
                        "remotePort", config.remotePort(),
                        "apiBase", config.apiBase(),
                        "frpHost", config.frpHost(),
                        "email", config.email()
                )
        ));
    }
}
