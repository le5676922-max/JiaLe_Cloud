# JiaLe Cloud 全局配置

> 改一处，全局生效。所有配置集中在此文件。

## 首次配置

1. 复制 `.env` 到项目根目录：
```
cp .env .env.local
```

2. 编辑 `.env.local`，填入你的服务器 IP 和密码

3. 所有脚本和配置自动从 `.env.local` 读取

## 服务器地址（从 .env 读取）

| 位置 | 配置方式 |
|------|---------|
| `yunpan/src/.../application.yaml` | `yunpan.cloud.host` 通过环境变量 `${CLOUD_HOST}` 配置 |
| `Cun/setup.sh` | 自动读取 `CLOUD_HOST` 环境变量 |
| `deploy/node/setup.sh` | 同上 |
| Android App | 设置页面手动输入 |

## 数据库

| 位置 | 配置项 | 当前值 |
|------|--------|--------|
| `yunpan/src/.../application.yaml` | `spring.datasource.url` | `jdbc:mysql://localhost:3306/yunpan` |
| `yunpan/src/.../application.yaml` | `spring.datasource.username` | `root` |
| `yunpan/src/.../application.yaml` | `spring.datasource.password` | `your-db-password`（通过 `${DB_PASSWORD}` 环境变量） |
| `yunpan/sql/init.sql` | 管理员密码 | 通过 `ADMIN_PASSWORD` 环境变量（SHA-256 双重哈希存储） |

## 加密密钥

| 位置 | 配置项 | 当前值 |
|------|--------|--------|
| `yunpan/src/.../application.yaml` | `yunpan.security.master-key` | 通过 `${MASTER_KEY}` 环境变量配置 |
| `Cun/setup.sh` | `master-key` | 通过 `${MASTER_KEY}` 环境变量配置 |

## JWT

| 位置 | 配置项 | 当前值 |
|------|--------|--------|
| `yunpan/src/.../application.yaml` | `yunpan.jwt.secret` | 通过 `${JWT_SECRET}` 环境变量配置 |
| `yunpan/src/.../application.yaml` | `yunpan.jwt.expiration-ms` | `604800000`（7天） |

## 邮件

| 位置 | 配置项 | 当前值 |
|------|--------|--------|
| `yunpan/src/.../application.yaml` | `spring.mail.username` | 通过 `${MAIL_USERNAME}` 环境变量配置 |

## frp 端口分配

| 节点 | frpc remote_port |
|------|-----------------|
| 云服务器 MySQL 转发 | `13306` |
| 存储节点 1 | `18080` |
| 存储节点 2 | `18081` |
| 存储节点 3 | `18082` |
| ... | `18083`-`18089` |

## 阿里云安全组

| 端口 | 协议 | 用途 |
|------|------|------|
| `80` | TCP | HTTP |
| `443` | TCP | frp 服务端 |
| `13306` | TCP | MySQL frp 转发 |
| `18080-18089` | TCP | 存储节点 |

## 改 IP 只需改 1 处

1. 编辑 `.env` 文件中的 `CLOUD_HOST` 和 `CLOUD_FRP_HOST`

所有配置文件通过环境变量引用，无需手动修改。
