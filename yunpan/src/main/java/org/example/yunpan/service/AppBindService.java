package org.example.yunpan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.yunpan.entity.AppBindTicket;
import org.example.yunpan.entity.Node;
import org.example.yunpan.mapper.AppBindTicketMapper;
import org.example.yunpan.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * App 扫码绑定服务。
 *
 * <h3>节点生命周期</h3>
 * <pre>
 *   创建 ticket → 消费 ticket (upsertBoundNode) → [activating] → register → [online] → heartbeat (每30s)
 *                    ↑ 初始状态 activating                        ↑ 首次注册激活
 *                                                                   ↓ 心跳超时 90s
 *                                                                 [offline] → detectOfflineNodes 清理
 * </pre>
 *
 * <h3>owner 字段说明</h3>
 * <p>当前所有 App 绑定的节点 owner 均为创建 ticket 的管理员邮箱。
 * 存储路由 ({@link org.example.yunpan.service.StorageRouter#selectNode}) 不按 owner 过滤，
 * 所有 online 节点对所有用户共享。</p>
 */
@Service
public class AppBindService {
    private final AppBindTicketMapper ticketMapper;
    private final NodeMapper nodeMapper;
    private final AppBindTokenCodec codec;
    private final long ticketTtlSeconds;
    private final int remotePortStart;
    private final String apiBase;
    private final String frpHost;

    public AppBindService(AppBindTicketMapper ticketMapper,
                          NodeMapper nodeMapper,
                          AppBindTokenCodec codec,
                          @Value("${yunpan.bind.ticket-ttl-seconds:300}") long ticketTtlSeconds,
                          @Value("${yunpan.bind.remote-port-start:18082}") int remotePortStart,
                          @Value("${yunpan.bind.api-base:}") String apiBase,
                          @Value("${yunpan.bind.frp-host:}") String frpHost,
                          @Value("${yunpan.cloud.host:}") String cloudHost) {
        this.ticketMapper = ticketMapper;
        this.nodeMapper = nodeMapper;
        this.codec = codec;
        this.ticketTtlSeconds = ticketTtlSeconds;
        this.remotePortStart = remotePortStart;
        this.apiBase = normalizeApiBase(apiBase, cloudHost);
        this.frpHost = normalizeHost(frpHost == null || frpHost.isBlank() ? cloudHost : frpHost);
    }

    public BindTicket createTicket(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户不能为空");
        }
        String ticket = codec.newToken();
        String nodeToken = codec.newToken();
        String nodeId = "phone-" + codec.newToken().substring(0, 12);

        AppBindTicket record = new AppBindTicket();
        record.setTicketHash(codec.hash(ticket));
        record.setUsername(username.trim());
        record.setNodeId(nodeId);
        record.setRemotePort(nextRemotePort());
        record.setNodeToken(nodeToken);
        record.setUsed(false);
        record.setExpiresAt(LocalDateTime.now().plusSeconds(ticketTtlSeconds));
        ticketMapper.insert(record);

        return new BindTicket(ticket, "yunpan://bind?ticket=" + ticket, record.getExpiresAt());
    }

    public BindConfig consumeTicket(String rawTicket) {
        return consumeTicket(rawTicket, null);
    }

    /**
     * 消费一次性绑定码，完成节点绑定。
     *
     * @param rawTicket       绑定码或 yunpan://bind?ticket=... 深链接
     * @param existingNodeId  设备之前绑定的旧节点 ID，非空时后端自动清理孤儿节点
     */
    public BindConfig consumeTicket(String rawTicket, String existingNodeId) {
        String ticket = codec.extractTicket(rawTicket);
        if (ticket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定码无效");
        }
        AppBindTicket record = ticketMapper.selectOne(
                new LambdaQueryWrapper<AppBindTicket>().eq(AppBindTicket::getTicketHash, codec.hash(ticket)));
        if (record == null || Boolean.TRUE.equals(record.getUsed())
                || record.getExpiresAt() == null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定码已失效");
        }

        record.setUsed(true);
        record.setUsedAt(LocalDateTime.now());
        ticketMapper.updateById(record);
        upsertBoundNode(record);

        // 清理设备之前的旧绑定节点（孤儿节点）
        cleanOrphanNode(existingNodeId, record.getUsername());

        return new BindConfig(record.getNodeId(), record.getNodeToken(), record.getRemotePort(),
                apiBase, frpHost, record.getUsername());
    }

    /**
     * 清理同一用户同一设备的旧节点。
     * 仅当旧节点属于同一 owner 且处于非活跃状态（activating/offline）时才删除，
     * 防止误删其他设备或在线节点。
     */
    private void cleanOrphanNode(String existingNodeId, String owner) {
        if (existingNodeId == null || existingNodeId.isBlank() || owner == null || owner.isBlank()) {
            return;
        }
        String normalizedId = existingNodeId.trim();
        Node oldNode = nodeMapper.selectById(normalizedId);
        if (oldNode == null) return;
        // 安全检查：旧节点必须属于同一用户
        if (!owner.trim().equals(oldNode.getOwner())) return;
        // 仅清理非活跃节点；online 节点不应被覆盖绑定
        if ("online".equals(oldNode.getStatus())) {
            return;
        }
        nodeMapper.deleteById(normalizedId);
    }

    private void upsertBoundNode(AppBindTicket record) {
        Node node = nodeMapper.selectById(record.getNodeId());
        if (node == null) {
            node = new Node();
            node.setId(record.getNodeId());
            node.setName(record.getNodeId());
            node.setFrpPort(record.getRemotePort());
            node.setOwner(record.getUsername());
            node.setNodeToken(record.getNodeToken());
            // 初始状态为 activating：App 尚未启动注册，等待首次 register 变为 online
            node.setStatus("activating");
            node.setDeviceTotal(0L);
            node.setDeviceFree(0L);
            node.setQuota(0L);
            node.setUsedCapacity(0L);
            nodeMapper.insert(node);
            return;
        }
        // 已存在节点：更新端口、归属和 token，重置为 activating 等待 App 重新注册
        node.setFrpPort(record.getRemotePort());
        node.setOwner(record.getUsername());
        node.setNodeToken(record.getNodeToken());
        node.setStatus("activating");
        nodeMapper.updateById(node);
    }

    private int nextRemotePort() {
        int max = remotePortStart - 1;
        List<Node> nodes = nodeMapper.selectList(null);
        if (nodes != null) {
            for (Node node : nodes) {
                if (node.getFrpPort() != null && node.getFrpPort() > max) max = node.getFrpPort();
            }
        }
        List<AppBindTicket> activeTickets = ticketMapper.selectList(
                new LambdaQueryWrapper<AppBindTicket>()
                        .eq(AppBindTicket::getUsed, false)
                        .gt(AppBindTicket::getExpiresAt, LocalDateTime.now()));
        if (activeTickets != null) {
            for (AppBindTicket ticket : activeTickets) {
                if (ticket.getRemotePort() != null && ticket.getRemotePort() > max) {
                    max = ticket.getRemotePort();
                }
            }
        }
        return max + 1;
    }

    private String normalizeApiBase(String value, String cloudHost) {
        String base = value == null || value.isBlank() ? cloudHost : value;
        if (base == null || base.isBlank()) return "";
        base = base.trim().replaceAll("/+$", "");
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        return base;
    }

    private String normalizeHost(String value) {
        if (value == null) return "";
        return value.trim().replace("http://", "").replace("https://", "").replaceAll("/+$", "");
    }

    public record BindTicket(String ticket, String deepLink, LocalDateTime expiresAt) {}

    public record BindConfig(String nodeId, String nodeToken, Integer remotePort,
                             String apiBase, String frpHost, String email) {}
}
