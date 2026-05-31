-- App 扫码绑定升级脚本
-- 使用方式: mysql -u root -p yunpan < upgrade-app-bind.sql

SET @has_node_token := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'nodes'
      AND COLUMN_NAME = 'node_token'
);
SET @ddl := IF(@has_node_token = 0,
    'ALTER TABLE `nodes` ADD COLUMN `node_token` VARCHAR(128) NULL COMMENT ''节点独立鉴权 Token'' AFTER `owner`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `app_bind_tickets` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `ticket_hash` CHAR(64)     NOT NULL COMMENT '一次性绑定码 SHA-256，不保存明文',
    `username`    VARCHAR(128) NOT NULL COMMENT '绑定用户邮箱',
    `node_id`     VARCHAR(32)  NOT NULL COMMENT '预分配节点ID',
    `remote_port` INT          NOT NULL COMMENT '预分配 frp 远程端口',
    `node_token`  VARCHAR(128) NOT NULL COMMENT '节点独立鉴权 Token',
    `used`        TINYINT(1)   NOT NULL DEFAULT 0,
    `expires_at`  DATETIME     NOT NULL,
    `used_at`     DATETIME     NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_hash` (`ticket_hash`),
    UNIQUE KEY `uk_node_id` (`node_id`),
    INDEX `idx_active_ticket` (`used`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App扫码绑定票据表';
