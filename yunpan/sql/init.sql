-- ==================== JiaLe Cloud 数据库初始化 ====================
-- 使用方式: mysql -u root -p < init.sql
-- 安全：重复执行不会删除已有数据；仅创建缺失的表和默认管理员
-- 注意：本脚本不做旧表结构迁移，已有表需要升级字段时请单独执行 ALTER

CREATE DATABASE IF NOT EXISTS yunpan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE yunpan;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email`         VARCHAR(128) NOT NULL COMMENT '邮箱(登录账号)',
    `username`      VARCHAR(64)  NULL     COMMENT '显示名(可选)',
    `password`      VARCHAR(128) NOT NULL COMMENT '密码(SHA-256双哈希)',
    `encrypted_key` VARCHAR(256) NULL     COMMENT '文件密钥(密码哈希加密)',
    `recovery_key`  VARCHAR(256) NULL     COMMENT '文件密钥(服务端主密钥加密，用于密码重置和重启恢复)',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'ADMIN/USER',
    `membership`    VARCHAR(16)  NOT NULL DEFAULT 'FREE' COMMENT 'FREE/VIP',
    `quota`         BIGINT       NOT NULL DEFAULT 0 COMMENT '个人配额(字节), 0=不限',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文件位置表
CREATE TABLE IF NOT EXISTS `file_locations` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `username`     VARCHAR(128) NOT NULL COMMENT '用户邮箱',
    `file_path`    VARCHAR(1024) NOT NULL COMMENT '文件相对路径',
    `node_id`      VARCHAR(16)  NOT NULL DEFAULT 'phone' COMMENT 'phone/ubuntu(阶段2)',
    `size`         BIGINT       NOT NULL DEFAULT 0,
    `is_directory` TINYINT(1)   NOT NULL DEFAULT 0,
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '回收站标记',
    `deleted_at`   DATETIME     NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_path` (`username`, `file_path`(255)),
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件位置表';

-- 分片上传记录表
CREATE TABLE IF NOT EXISTS `upload_chunks` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(128) NOT NULL COMMENT '用户邮箱',
    `file_path`   VARCHAR(1024) NOT NULL COMMENT '目标路径',
    `upload_id`   VARCHAR(64)  NOT NULL COMMENT '上传会话ID',
    `chunk_index` INT          NOT NULL COMMENT '分片序号(0开始)',
    `chunk_size`  BIGINT       NOT NULL DEFAULT 0,
    `node_id`     VARCHAR(16)  NOT NULL DEFAULT 'phone',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_chunk` (`upload_id`, `chunk_index`),
    INDEX `idx_upload_id` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录';

-- 存储节点表
CREATE TABLE IF NOT EXISTS `nodes` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '节点ID, 如 phone1, phone2',
    `name`          VARCHAR(64)  NOT NULL COMMENT '显示名',
    `frp_port`      INT          NOT NULL COMMENT 'frp映射端口',
    `owner`         VARCHAR(128) NULL     COMMENT '所属用户邮箱，NULL=共享节点',
    `node_token`    VARCHAR(128) NULL     COMMENT '节点独立鉴权 Token',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'online/offline/pending',
    `device_total`  BIGINT       NOT NULL DEFAULT 0 COMMENT '设备总空间(字节)',
    `device_free`   BIGINT       NOT NULL DEFAULT 0 COMMENT '设备剩余空间',
    `quota`         BIGINT       NOT NULL DEFAULT 0 COMMENT '云盘可用额度 = 剩余×0.7',
    `used_capacity` BIGINT       NOT NULL DEFAULT 0 COMMENT '云盘已用空间',
    `last_heartbeat` DATETIME    NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储节点表';

-- App 扫码绑定票据表
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

-- 分享链接表
CREATE TABLE IF NOT EXISTS `share_links` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `file_path`  VARCHAR(1024) NOT NULL,
    `username`   VARCHAR(128)  NOT NULL,
    `token`      VARCHAR(64)   NOT NULL,
    `expires_at` DATETIME      NOT NULL,
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认管理员：后端启动不再依赖 application.yaml 自动创建管理员
-- 已存在同邮箱账号时不覆盖密码、角色或配额，避免重复执行重置线上账号
INSERT INTO `user` (`email`, `username`, `password`, `role`, `membership`, `quota`, `enabled`) VALUES
('admin@yunpan.local', 'admin', SHA2(SHA2('yunpan2026', 256), 256), 'ADMIN', 'VIP', 0, 1)
ON DUPLICATE KEY UPDATE `email` = `email`;
