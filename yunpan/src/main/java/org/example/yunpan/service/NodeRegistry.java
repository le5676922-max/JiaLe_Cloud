package org.example.yunpan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.yunpan.entity.Node;
import org.example.yunpan.mapper.NodeMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NodeRegistry {

    private final NodeMapper nodeMapper;

    public NodeRegistry(NodeMapper nodeMapper) {
        this.nodeMapper = nodeMapper;
    }

    /**
     * 节点注册（首次启动时调用）。
     * <p>
     * nodeToken 来源于 X-Node-Token 请求头，由 NodeController 传入。
     * 数据库保存该 token，使每个节点拥有独立的鉴权凭据，
     * 不再强依赖全局 yunpan.node.token。
     * </p>
     */
    public void register(String nodeId, String name, int frpPort, String owner, String nodeToken) {
        Node existing = nodeMapper.selectById(nodeId);
        if (existing == null) {
            Node node = new Node();
            node.setId(nodeId);
            node.setName(name);
            node.setFrpPort(frpPort);
            node.setOwner(owner);
            node.setNodeToken(nodeToken);
            // 个人节点（owner 不为空）自动批准上线；共享节点（owner 为空）需管理员手动批准
            node.setStatus(owner != null ? "online" : "pending");
            node.setDeviceTotal(0L);
            node.setDeviceFree(0L);
            node.setQuota(0L);
            node.setUsedCapacity(0L);
            nodeMapper.insert(node);
        } else {
            existing.setName(name);
            existing.setFrpPort(frpPort);
            // 只有 owner 从 null 变为非 null 才更新，避免覆盖 App 绑定时已设置的 owner
            if (owner != null) {
                existing.setOwner(owner);
            }
            // 保留已有的 nodeToken，仅在传入新 token 时更新
            if (nodeToken != null && !nodeToken.isBlank()) {
                existing.setNodeToken(nodeToken);
            }
            // 状态转换：pending/activating/offline → online（前提：有 owner 的个人节点自动激活）
            String currentStatus = existing.getStatus();
            if (owner != null) {
                // 有归属的个人节点：直接激活为 online
                existing.setStatus("online");
            } else if ("pending".equals(currentStatus)) {
                // 共享节点且仍为 pending：保持 pending，等待管理员手动批准
                // (不做修改)
            } else {
                // 共享节点且已非 pending（如 online/offline/activating）：保持原状态
                // (不做修改)
            }
            nodeMapper.updateById(existing);
        }
    }

    /**
     * 心跳上报（存储节点每 30s 调用）。
     * activating 和 online 状态的心跳都标记为 online。
     * pending 节点不上报心跳（尚未批准，App 端不会启动服务）。
     */
    public void heartbeat(String nodeId, long deviceTotal, long deviceFree, long usedCapacity) {
        Node node = nodeMapper.selectById(nodeId);
        if (node != null) {
            long quota = (long) (deviceFree * 0.7);
            node.setDeviceTotal(deviceTotal);
            node.setDeviceFree(deviceFree);
            node.setQuota(quota);
            node.setUsedCapacity(usedCapacity);
            node.setLastHeartbeat(LocalDateTime.now());
            if (!"pending".equals(node.getStatus())) {
                // activating / online / offline → 标记为 online
                node.setStatus("online");
            }
            // pending 节点：保持 pending，等待管理员批准
            nodeMapper.updateById(node);
        }
    }

    /**
     * 管理员批准节点上线
     */
    public void approve(String nodeId) {
        Node node = nodeMapper.selectById(nodeId);
        if (node != null) {
            node.setStatus("online");
            nodeMapper.updateById(node);
        }
    }

    /**
     * 管理员删除节点
     */
    public void remove(String nodeId) {
        nodeMapper.deleteById(nodeId);
    }

    public void unbind(String nodeId) {
        remove(nodeId);
    }

    /**
     * 获取所有 online 节点
     */
    public List<Node> getOnlineNodes() {
        return nodeMapper.selectList(
                new LambdaQueryWrapper<Node>().eq(Node::getStatus, "online"));
    }

    /**
     * 获取所有节点
     */
    public List<Node> getAllNodes() {
        return nodeMapper.selectList(null);
    }

    /**
     * 每 60 秒扫描超时节点。
     * <ul>
     *   <li>online / activating 节点：最后心跳超过 90 秒则标记为 offline</li>
     *   <li>activating 节点创建超过 10 分钟仍未首次心跳 → 标记为 offline（App 未启动）</li>
     * </ul>
     */
    @Scheduled(fixedRate = 60000)
    public void detectOfflineNodes() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(90);
        LocalDateTime activationCutoff = LocalDateTime.now().minusMinutes(10);

        // online 和 activating 节点超时检测
        List<Node> activeNodes = nodeMapper.selectList(
                new LambdaQueryWrapper<Node>()
                        .in(Node::getStatus, java.util.List.of("online", "activating")));
        for (Node node : activeNodes) {
            if (node.getLastHeartbeat() == null) {
                // activating 节点创建超过 10 分钟仍无首次心跳 → offline
                if ("activating".equals(node.getStatus())
                        && node.getCreatedAt() != null
                        && node.getCreatedAt().isBefore(activationCutoff)) {
                    node.setStatus("offline");
                    nodeMapper.updateById(node);
                }
                // (online 节点无 lastHeartbeat 不应出现，跳过)
            } else if (node.getLastHeartbeat().isBefore(cutoff)) {
                node.setStatus("offline");
                nodeMapper.updateById(node);
            }
        }
    }
}
