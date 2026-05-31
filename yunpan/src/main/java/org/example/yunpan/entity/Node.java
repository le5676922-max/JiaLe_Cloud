package org.example.yunpan.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("nodes")
public class Node {
    @TableId
    private String id;
    private String name;
    private Integer frpPort;
    private String status;
    private String owner;
    private String nodeToken;
    private Long deviceTotal;
    private Long deviceFree;
    private Long quota;
    private Long usedCapacity;
    private LocalDateTime lastHeartbeat;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getFrpPort() { return frpPort; }
    public void setFrpPort(Integer frpPort) { this.frpPort = frpPort; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getNodeToken() { return nodeToken; }
    public void setNodeToken(String nodeToken) { this.nodeToken = nodeToken; }
    public Long getDeviceTotal() { return deviceTotal; }
    public void setDeviceTotal(Long deviceTotal) { this.deviceTotal = deviceTotal; }
    public Long getDeviceFree() { return deviceFree; }
    public void setDeviceFree(Long deviceFree) { this.deviceFree = deviceFree; }
    public Long getQuota() { return quota; }
    public void setQuota(Long quota) { this.quota = quota; }
    public Long getUsedCapacity() { return usedCapacity; }
    public void setUsedCapacity(Long usedCapacity) { this.usedCapacity = usedCapacity; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
