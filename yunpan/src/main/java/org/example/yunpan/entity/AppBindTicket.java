package org.example.yunpan.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("app_bind_tickets")
public class AppBindTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketHash;
    private String username;
    private String nodeId;
    private Integer remotePort;
    private String nodeToken;
    private Boolean used;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTicketHash() { return ticketHash; }
    public void setTicketHash(String ticketHash) { this.ticketHash = ticketHash; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public Integer getRemotePort() { return remotePort; }
    public void setRemotePort(Integer remotePort) { this.remotePort = remotePort; }
    public String getNodeToken() { return nodeToken; }
    public void setNodeToken(String nodeToken) { this.nodeToken = nodeToken; }
    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
