package org.example.yunpan.service;

import org.example.yunpan.entity.Node;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class StorageRouter {

    private final NodeRegistry nodeRegistry;

    public StorageRouter(NodeRegistry nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
    }

    /**
     * 为上传请求选择最佳存储节点（全局负载均衡，不按 owner 过滤）。
     * 策略：容量比例负载均衡 — 选负载最低的 online 节点。
     * 负载 = 已用容量 / 配额（quota 为 0 则视为满）。
     *
     * @param fileSize 待上传文件大小(字节)
     * @return 最佳节点，若无可用节点返回 null
     */
    public Node selectNode(long fileSize) {
        List<Node> nodes = nodeRegistry.getOnlineNodes();
        if (nodes.isEmpty()) return null;

        return nodes.stream()
                .filter(n -> n.getQuota() > 0 && (n.getQuota() - n.getUsedCapacity()) >= fileSize)
                .min(Comparator.comparingDouble(n ->
                        n.getQuota() > 0 ? (double) n.getUsedCapacity() / n.getQuota() : 1.0))
                .orElse(null);
    }

    public Node selectNode(long fileSize, String requestedNodeId) {
        if (requestedNodeId == null || requestedNodeId.isBlank()) {
            return selectNode(fileSize);
        }
        String target = requestedNodeId.trim();
        return nodeRegistry.getOnlineNodes().stream()
                .filter(n -> target.equals(n.getId()))
                .filter(n -> n.getQuota() != null && n.getUsedCapacity() != null)
                .filter(n -> n.getQuota() > 0 && (n.getQuota() - n.getUsedCapacity()) >= fileSize)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "指定的存储节点不可用或容量不足"));
    }

    public Node selectOwnedNode(long fileSize, String owner) {
        if (owner == null || owner.isBlank()) return null;
        String targetOwner = owner.trim();
        return nodeRegistry.getOnlineNodes().stream()
                .filter(n -> targetOwner.equals(n.getOwner()))
                .filter(n -> n.getQuota() != null && n.getUsedCapacity() != null)
                .filter(n -> n.getQuota() > 0 && (n.getQuota() - n.getUsedCapacity()) >= fileSize)
                .min(Comparator.comparingDouble(n ->
                        n.getQuota() > 0 ? (double) n.getUsedCapacity() / n.getQuota() : 1.0))
                .orElse(null);
    }

    /**
     * 判断文件是否应该走分片上传（>100MB）
     */
    public boolean shouldChunk(long fileSize) {
        return fileSize > 100L * 1024 * 1024;
    }
}
