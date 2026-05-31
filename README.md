# JiaLe Cloud

> 🏠 把旧手机变成你的私人云盘，让闲置设备重获新生。

JiaLe Cloud是一个**分布式个人云存储系统**，核心思路是——旧安卓手机别扔，装上JiaLe Cloud App，立刻变成一个加密存储节点。多台手机组成你的专属分布式存储网络，数据端到端加密，完全由你掌控。

[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 目录

- [为什么选择JiaLe Cloud？](#为什么选择JiaLe Cloud)
- [系统架构](#系统架构)
- [目录结构](#目录结构)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [安全架构](#安全架构)
- [快速开始](#快速开始)
- [详细使用指南](#详细使用指南)
  - [1. 注册账户](#1-注册账户)
  - [2. 登录系统](#2-登录系统)
  - [3. 上传文件](#3-上传文件)
  - [4. 下载文件](#4-下载文件)
  - [5. 分享文件](#5-分享文件)
  - [6. 回收站管理](#6-回收站管理)
  - [7. 添加 Android 手机存储节点](#7-添加-android-手机存储节点)
  - [8. 管理面板](#8-管理面板)
- [部署指南](#部署指南)
- [API 接口文档](#api-接口文档)
- [数据库设计](#数据库设计)
- [常见问题](#常见问题)
- [未来规划](#未来规划)
- [License](#license)

---

## 为什么选择JiaLe Cloud？

| 特性 | 说明 |
|------|------|
| 🔐 **端到端加密** | 文件密钥在浏览器端生成，AES-256-GCM 加密后存到服务器。服务器只存储密文，**永远无法解密你的文件** |
| 📱 **旧手机变废为宝** | 闲置安卓手机安装 App 即成存储节点，零额外成本无限扩容 |
| 🧩 **分布式负载均衡** | 文件智能分配到不同节点，基于容量比例的加权负载均衡算法 |
| ⚡ **分块上传 + 断点续传** | 大文件（>100MB）自动分 10MB 块上传，5 线程并行，中断后可续传 |
| 🔗 **加密分享** | 生成限时分享链接，支持 1小时/24小时/7天/30天 过期 |
| 🗑️ **回收站保护** | 软删除机制，7 天内可恢复，每日凌晨 3 点自动清理过期文件 |
| 👑 **完善的管理后台** | 用户管理、节点审批、配额控制、文件浏览一站式管理 |
| 🔄 **单设备登录** | 新登录自动踢掉旧会话，防止账号泄露风险 |

---

## 系统架构

```
┌────────────────────────────────────────────────────────────────────┐
│                          用户浏览器                                  │
│                    Vue 3 SPA (Element Plus)                         │
│              客户端加密 • 分块上传 • 扫码绑定                           │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ HTTPS
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     ☁️ 协调服务器 (yunpan)                             │
│                                                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ Auth     │ │ File     │ │ Chunk    │ │ Admin    │ │ Node     │  │
│  │ Controller│ │ Controller│ │ Upload   │ │ Controller│ │ Controller│  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  SessionManager • StorageRouter • CryptoService • JwtUtils   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    MySQL (yunpan)                              │   │
│  │  user • nodes • file_locations • upload_chunks • share_links │   │
│  │  app_bind_tickets                                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────┬────────────────────────────────┬──────────────────────────────┘
       │ frp 隧道 (KCP over HTTPS)      │ frp 隧道
       ▼                                ▼
┌──────────────────────┐    ┌──────────────────────┐
│  📱 Android 存储节点   │    │  💻 Termux/Linux 节点  │
│                      │    │                      │
│  NodeService(前台)    │    │  Cun (Spring Boot)   │
│  NodeHttpServer      │    │  FileStorageService  │
│  NativeStorage       │    │  NodeAgent(心跳)     │
│  FrpcManager         │    │  frpc 隧道           │
│  AppBindClient       │    │                      │
│  (AES-GCM 加密存储)   │    │  (AES-GCM 加密存储)   │
└──────────────────────┘    └──────────────────────┘
```

**核心流程：**

```
上传大文件:
  浏览器 → /upload/init → 协调服务器分配节点
        → 直连存储节点上传分块 (5线程并行, 10MB/块)
        → /upload/merge → 节点解密分块 → 合并 → AES-GCM加密 → 写入磁盘
        → 节点回调协调服务器记录文件位置

下载文件:
  浏览器 → POST /files/download-token → 获取5分钟签名URL
        → GET /files/dl/{token} → 协调服务器检查文件位置
        → 检测加密标记(0x01) → AES-GCM解密 → 流式返回
```

---

## 目录结构

```
Yun_pan/
├── yunpan/                       # ☁️ 协调服务器 (Spring Boot)
│   ├── src/main/java/org/example/yunpan/
│   │   ├── YunpanApplication.java      # 启动入口
│   │   ├── config/                     # 拦截器、会话管理、数据库配置
│   │   ├── controller/                 # REST API 控制器
│   │   │   ├── AuthController.java     #   登录/登出
│   │   │   ├── RegisterController.java #   注册/邮箱验证码
│   │   │   ├── FileController.java     #   文件CRUD/上传/下载/分享
│   │   │   ├── AdminController.java    #   管理面板
│   │   │   ├── NodeController.java     #   节点注册/心跳
│   │   │   └── AppBindController.java  #   扫码绑定票据
│   │   ├── service/                    # 业务逻辑
│   │   │   ├── FileService.java        #   核心文件操作
│   │   │   ├── ChunkUploadService.java #   分块上传
│   │   │   ├── CryptoService.java      #   AES-256-GCM 加解密
│   │   │   ├── StorageRouter.java      #   节点负载均衡
│   │   │   ├── NodeRegistry.java       #   节点生命周期管理
│   │   │   └── AppBindService.java     #   绑定票据服务
│   │   ├── entity/                     # 数据库实体 (6张表)
│   │   ├── mapper/                     # MyBatis-Plus Mapper
│   │   └── util/                       # JWT 工具类
│   ├── src/main/resources/
│   │   ├── application.yaml            # 主配置文件
│   │   └── static/                     # 编译后的 Vue SPA
│   └── sql/
│       └── init.sql                    # 数据库建表 + 默认管理员
│
├── frontend/                     # 🖥️ Web 前端 (Vue 3 + Vite)
│   ├── src/
│   │   ├── views/
│   │   │   ├── Login.vue               # 登录页
│   │   │   ├── Register.vue            # 注册页 (2步向导)
│   │   │   ├── Home.vue                # 文件管理主页
│   │   │   ├── Admin.vue               # 管理面板
│   │   │   └── Join.vue                # 加入存储网络指引
│   │   ├── utils/
│   │   │   ├── crypto.ts               # 客户端加密 (SHA-256 + AES-GCM)
│   │   │   ├── chunkUpload.ts          # 分块上传 (断点续传)
│   │   │   └── request.ts              # Axios HTTP 封装
│   │   ├── router/index.ts             # 路由 + 权限守卫
│   │   └── styles/theme.css            # 自定义主题
│   └── vite.config.ts
│
├── Cun/                          # 💻 独立存储节点 (Spring Boot)
│   └── src/main/java/org/example/cun/
│       ├── CunApplication.java          # 启动入口
│       ├── FileController.java          # 文件 REST 接口
│       ├── FileStorageService.java      # 文件存储 + 加解密
│       ├── NodeAgent.java               # 自动注册 + 心跳上报
│       └── AuthService.java             # JWT 验证
│
├── Yunpan_App/                   # 📱 Android 存储节点 App
│   └── app/src/main/java/com/example/yunpan_app/
│       ├── MainActivity.java            # 主页 (Tab 导航)
│       ├── NodeService.java             # 前台服务
│       ├── NodeHttpServer.java          # 内嵌 HTTP 服务器
│       ├── NativeStorage.java           # 文件存储 + 加密
│       ├── FrpcManager.java             # frpc 进程管理
│       ├── AppBindClient.java           # 扫码绑定
│       └── BootReceiver.java            # 开机自启
│
├── deploy/                       # 🚀 部署脚本
│   └── node/
│       ├── setup.sh                     # Termux 一键安装
│       └── watchdog.sh                  # 进程监控守护
│
├── tools/                        # 🔧 工具目录
├── .env.example                  # 环境变量模板 (安全可上传)
├── .gitignore                    # Git 忽略规则
├── CONFIG.md                     # 集中配置说明
├── README.md                     # 本文件
├── health-check.sh               # 生产环境健康检查
└── test-local.sh                 # 本地最小闭环测试
```

---

## 技术栈

### 后端 (协调服务器)

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 4.0.6 | Web 框架 |
| MyBatis-Plus | 3.5.14 | ORM (自动 CRUD) |
| MySQL | 8.0 | 关系数据库 |
| JJWT | 0.12.6 | JWT 令牌 |
| Spring Mail | - | QQ 邮箱验证码 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.13 | Composition API + `<script setup>` |
| TypeScript | 5.7 | 类型安全 |
| Element Plus | 2.9.7 | UI 组件库 |
| Vite | 6.3.5 | 构建工具 |
| Vue Router | 4.5 | 路由管理 |
| Axios | 1.9 | HTTP 客户端 |
| js-sha256 | 0.11 | 客户端密码哈希 |
| QRCode | 1.5 | 二维码生成 |

### 存储节点

| 平台 | 技术 | 说明 |
|------|------|------|
| Linux/Termux | Spring Boot 4.0.6 | 独立 HTTP 服务 |
| Android | Java 11, AppCompat | 前台服务 + 内嵌 HTTP Server |
| 内网穿透 | frp (KCP) | NAT 穿透 |

---

## 核心功能

### 📤 文件上传

- **小文件直传** (<100MB)：直接 POST 到协调服务器
- **大文件分块** (≥100MB)：10MB/块，5 线程并行，**断点续传**
- **智能压缩**：上传前自动 Deflate 压缩（跳过图片/视频/音频/压缩包）
- **实时加密**：压缩后 → AES-256-GCM 加密 → 写入磁盘
- **节点穿透上传**：分块上传直连存储节点（frp 隧道），绕开协调服务器，更快

### 📥 文件下载

- **签名 URL**：下载前获取 5 分钟有效期签名 Token，防止盗链
- **流式解密**：检测加密标记 → 读取 nonce → AES-GCM 解密 → 逐块返回
- **透明解压**：自动检测并解压 Deflate 压缩的文件
- **远程节点代理**：文件在其他节点时，协调服务器代理下载

### 🔗 文件分享

- 生成 12 位随机 Token 链接
- 支持过期时间：1 小时 / 24 小时 / 7 天 / 30 天
- 公开访问，无需登录

### 🗑️ 回收站

- 删除文件进入回收站（软删除，保留 7 天）
- 支持恢复和永久删除
- 每日凌晨 3 点自动清理过期文件

### 📊 容量管理

| 会员等级 | 配额 |
|---------|------|
| FREE | 5 GB |
| VIP | 30 GB |

### 📱 存储节点管理

- **个人节点**：注册后自动激活，独享使用
- **共享节点**：需管理员审批后激活
- **心跳检测**：每 30 秒上报，90 秒无心跳自动离线
- **负载均衡**：基于 (已用容量 / 配额) 比例选择最优节点

---

## 安全架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        安全层次                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1️⃣ 传输层                                                       │
│     HTTPS + frp KCP 隧道加密                                      │
│                                                                 │
│  2️⃣ 认证层                                                       │
│     JWT Bearer Token (HS256, 7天过期)                             │
│     单设备登录 (新登录踢掉旧会话)                                     │
│     节点独立 Token + 恒时比较防时序攻击                               │
│                                                                 │
│  3️⃣ 密码层                                                       │
│     客户端 SHA-256 哈希 → 服务端再 SHA-256 哈希                      │
│     明文密码从不离开浏览器                                           │
│                                                                 │
│  4️⃣ 加密层                                                       │
│     文件密钥在浏览器 Web Crypto API 生成 (AES-256-GCM)               │
│     文件密钥用密码哈希加密 → 存到服务器 (服务器无法解开)                  │
│     文件内容用文件密钥 AES-256-GCM 加密后存储                          │
│     Recovery Key: 文件密钥用 Master Key 二次加密 (用于密码重置)        │
│                                                                 │
│  5️⃣ 应用层                                                       │
│     文件路径规范化 + 遍历防护                                          │
│     绑定票据 SHA-256 哈希存储 (明文不落库)                              │
│     下载 Token 5 分钟有效期                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**加密数据流：**

```
注册时:
  浏览器: generateKey() → AES-256 文件密钥
          password → SHA-256 → 密码哈希
          文件密钥 + 密码哈希 → AES-GCM 加密 → encryptedKey (发到服务器)
  服务器: 文件密钥 + MasterKey → AES-GCM 加密 → recoveryKey (存数据库)

上传时:
  文件 → Deflate 压缩 → AES-256-GCM(文件密钥) 加密 → 写入磁盘 (0x01 标记)

下载时:
  读取磁盘 → 检测 0x01 标记 → 读取 nonce → AES-256-GCM(文件密钥) 解密 → 解压 → 返回

密码重置:
  服务器: recoveryKey + MasterKey → AES-GCM 解密 → 还原文件密钥
         文件密钥 + 新密码哈希 → AES-GCM 加密 → 新的 encryptedKey
  ✅ 管理员重置密码后用户文件可正常访问，不会丢失数据
```

---

## 快速开始

### 前置要求

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+ (前端开发)
- Android Studio (可选，编译 App)

### 1. 环境配置

```bash
# 克隆仓库
git clone git@github.com:le5676922-max/-.git
cd -

# 配置环境变量
cp .env.example .env
# 编辑 .env，填入你的真实配置
```

### 2. 初始化数据库

```bash
mysql -u root -p < yunpan/sql/init.sql
```

这会创建 `yunpan` 数据库、6 张表，以及默认管理员账号：
- 邮箱：`admin@yunpan.local`
- 密码：`yunpan2026`

### 3. 启动协调服务器

```bash
cd yunpan
mvn spring-boot:run
```

服务启动在 `http://localhost:8080`

### 4. 启动前端开发服务器

```bash
cd frontend
npm install
npm run dev
```

前端启动在 `http://localhost:5173`，API 代理到 `localhost:8080`

### 5. 本地闭环测试

```bash
bash test-local.sh
```

该脚本会同时编译并启动 yunpan + Cun，完成登录 → 上传 → 验证存储的完整闭环测试。

---

## 详细使用指南

### 1. 注册账户

1. 打开 http://localhost:5173 ，点击"注册"
2. 输入邮箱，点击"发送验证码"（60 秒冷却期）
3. 检查邮箱，填入 6 位验证码
4. 设置密码

在注册时，浏览器会自动：
- SHA-256 哈希密码（明文密码立即清除）
- 通过 Web Crypto API 生成 AES-256 文件密钥
- 用密码哈希加密文件密钥

### 2. 登录系统

1. 输入邮箱和密码
2. 浏览器 SHA-256 哈希密码后发送
3. 服务器验证后返回 JWT Token
4. 自动解密文件密钥并缓存到服务端会话

**角色路由：**
- `USER` → 文件管理页 (`/`)
- `ADMIN` → 管理面板 (`/admin`)

### 3. 上传文件

**小文件上传 (<100MB)：**
1. 在文件管理页，点击"上传"按钮或拖拽文件到上传区域
2. 文件自动上传，显示进度条

**大文件分块上传 (≥100MB)：**
1. 系统自动检测并启用分块模式
2. 分 10MB 块、5 线程并行上传
3. 如果有在线存储节点，分块直连节点（更快）
4. 支持断点续传：中断后重新上传，自动跳过已完成的块
5. 全部块上传完毕后，调用 merge 接口合并

**上传冲突处理：**
- 同名文件弹出对话框，选择"覆盖"或"重命名"

### 4. 下载文件

1. 在文件列表中点击文件名
2. 系统生成 5 分钟有效的下载签名 Token
3. 浏览器通过签名 URL 下载
4. 加密文件自动解密后保存到本地

**注意：** 如果文件在远程存储节点上，协调服务器会代理下载（通过 frp 隧道）。

### 5. 分享文件

1. 在文件列表中点击"分享"
2. 选择过期时间：1 小时 / 24 小时 / 7 天 / 30 天
3. 生成分享链接，复制分享给他人
4. 接收者无需登录即可下载

### 6. 回收站管理

1. 点击"回收站"按钮查看已删除文件
2. **恢复**：点击"恢复"，文件回到原位置
3. **永久删除**：点击"永久删除"，不可逆
4. 超过 7 天的文件自动清理（每日凌晨 3 点）

### 7. 添加 Android 手机存储节点

这是JiaLe Cloud的核心功能——用旧安卓手机当存储节点。

#### 方式一：二维码扫码绑定（推荐）

1. 在管理面板 (`/admin`) → "节点管理" → 点击"生成绑定码"
2. 弹出二维码和文本绑定码
3. 在安卓手机上打开JiaLe Cloud App
4. 扫码或手动输入绑定码
5. App 自动注册为存储节点，开始接收文件

#### 方式二：Termux 命令行绑定

在旧安卓手机上安装 Termux，然后：

```bash
# 方式A：下载一键安装脚本
curl -O http://你的服务器IP:8080/setup.sh
bash setup.sh 你的邮箱

# 方式B：手动安装
pkg install openjdk-17 wget
wget http://你的服务器IP:8080/cun.jar
# 启动节点
java -jar cun.jar
```

安装脚本自动完成：
1. 安装 Java 17
2. 下载存储节点 JAR
3. 配置 frp 内网穿透
4. 自动注册到协调服务器
5. 启动心跳（每 30 秒）
6. 部署看门狗进程（自动重启崩溃的节点）

### 8. 管理面板

仅 ADMIN 角色可访问 (`/admin`)：

#### 仪表盘
- 总用户数 / 启用用户数 / VIP 用户数 / 禁用用户数

#### 用户管理
- 查看所有用户列表
- 创建/编辑用户：邮箱、密码、角色(ADMIN/USER)、会员(FREE/VIP)、配额
- 重置密码：保留加密文件密钥（通过 Recovery Key 恢复）
- 删除用户
- 浏览用户文件

#### 节点管理
- 查看所有存储节点：名称、端口、状态、容量、所有者
- 审批待激活节点
- 移除节点
- 生成绑定二维码

#### 文件浏览
- 按用户邮箱浏览其所有文件
- 面包屑导航

---

## 部署指南

### 云服务器部署

**环境：阿里云 ECS / AWS EC2 (建议 2核4G + 40G SSD)**

```bash
# 1. 安装 Java 17
yum install -y java-17-openjdk  # CentOS
# 或
apt install -y openjdk-17-jdk   # Ubuntu

# 2. 安装 MySQL 8.0
# (略，参考 MySQL 官方文档)

# 3. 初始化数据库
mysql -u root -p < yunpan/sql/init.sql

# 4. 编译打包
cd yunpan && mvn package -DskipTests
cp target/yunpan-*.jar /opt/yunpan/yunpan.jar

# 5. 配置 .env
cp .env.example .env
vim .env  # 修改所有密码和密钥为随机字符串

# 6. 启动
cd /opt/yunpan
nohup java -Xmx256m -jar yunpan.jar > yunpan.log 2>&1 &

# 7. 部署 frp 服务端（用于节点穿透）
# 参见 https://github.com/fatedier/frp
# frps 监听 443 端口 (KCP)

# 8. 设置健康检查
crontab -e
# 添加: */5 * * * * bash /opt/yunpan/health-check.sh
```

### 安全组配置

| 端口 | 协议 | 用途 |
|------|------|------|
| 80 | TCP | HTTP (Web 访问) |
| 443 | TCP | frp 服务端 (KCP 隧道) |
| 13306 | TCP | MySQL frp 转发 |
| 18080-18089 | TCP | 存储节点 frp 隧道 |

---

## API 接口文档

所有 API 请求格式为 `application/json`，认证方式为 `Authorization: Bearer <JWT>`。

### 认证接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 无 | 登录，返回 JWT + 角色 + 会员等级 |
| POST | `/api/auth/logout` | 无 | 销毁会话 |

### 注册接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/register/send-code` | 无 | 发送邮箱验证码 (60s 冷却) |
| POST | `/api/register` | 无 | 注册新用户 |

### 文件接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/files/list?path=` | JWT | 列出目录文件 |
| POST | `/api/files/upload` | JWT | 上传文件 (FormData) |
| GET | `/api/files/download?path=` | JWT | 下载文件 |
| POST | `/api/files/download-token` | JWT | 获取下载签名 Token (5分钟) |
| GET | `/api/files/dl/{token}` | 无 | 签名下载 |
| POST | `/api/files/folder` | JWT | 创建文件夹 |
| DELETE | `/api/files` | JWT | 软删除到回收站 |
| GET | `/api/files/trash` | JWT | 回收站列表 |
| POST | `/api/files/restore` | JWT | 从回收站恢复 |
| DELETE | `/api/files/permanent` | JWT | 永久删除 |
| PUT | `/api/files/rename` | JWT | 重命名 |
| GET | `/api/files/capacity` | JWT | 容量统计 |
| GET | `/api/files/exists?path=` | JWT | 检查文件是否存在 |
| POST | `/api/files/share` | JWT | 创建分享链接 |
| GET | `/api/share/{token}` | 无 | 通过分享链接下载 |

### 分块上传接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/upload/init` | JWT | 初始化分块上传会话 |
| POST | `/api/upload/chunk` | JWT | 上传单个分块 |
| POST | `/api/upload/merge` | JWT | 合并所有分块 |
| GET | `/api/upload/resume?upload_id=` | JWT | 查询已完成分块列表 |

### 节点接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/node/register` | Node Token | 节点自注册 |
| POST | `/api/node/heartbeat` | Node Token | 心跳上报 (30s) |
| POST | `/api/node/unbind` | Node Token | 节点解绑 |
| POST | `/api/node/files` | Node Token | 记录文件在节点上 |
| POST | `/api/node/auth-user` | Node Token | 验证用户 JWT |

### 绑定接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/app-bind/tickets` | JWT | 创建绑定票据 |
| POST | `/api/app-bind/consume` | Ticket | 消费票据，完成绑定 |
| GET | `/api/app-bind/nodes` | JWT | 列出我的节点 |

### 管理接口（ADMIN 角色）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/admin/stats` | JWT+ADMIN | 系统统计 |
| GET | `/api/admin/users` | JWT+ADMIN | 用户列表 |
| POST | `/api/admin/users` | JWT+ADMIN | 创建用户 |
| PUT | `/api/admin/users/{email}` | JWT+ADMIN | 更新用户 |
| POST | `/api/admin/users/{email}/reset-password` | JWT+ADMIN | 重置密码 (保留文件) |
| DELETE | `/api/admin/users/{email}` | JWT+ADMIN | 删除用户 |
| GET | `/api/admin/files?user=&path=` | JWT+ADMIN | 浏览指定用户文件 |
| GET | `/api/admin/nodes` | JWT+ADMIN | 所有节点 |
| POST | `/api/admin/nodes/{id}/approve` | JWT+ADMIN | 审批节点 |
| DELETE | `/api/admin/nodes/{id}` | JWT+ADMIN | 删除节点 |

---

## 数据库设计

**数据库：** `yunpan` (utf8mb4)

| 表名 | 字段 | 说明 |
|------|------|------|
| `user` | id, email, username, password, encrypted_key, recovery_key, role, membership, quota, enabled, create_time, update_time | 用户表 |
| `nodes` | id, name, frp_port, owner, node_token, status, device_total, device_free, quota, used_capacity, last_heartbeat, created_at | 存储节点表 |
| `file_locations` | id, username, file_path, node_id, size, is_directory, deleted, deleted_at, created_at | 文件位置表 |
| `upload_chunks` | id, username, file_path, upload_id, chunk_index, chunk_size, node_id, created_at | 分块上传记录表 |
| `share_links` | id, file_path, username, token, expires_at, created_at | 分享链接表 |
| `app_bind_tickets` | id, ticket_hash, username, node_id, remote_port, node_token, used, expires_at, used_at, created_at | 绑定票据表 |

**节点状态流转：**

```
pending ──(管理员审批)──► online
                        online ──(90s无心跳)──► offline
activating ──(首次注册成功)──► online
```

---

## 常见问题

### Q: 我的文件安全吗？服务器能看到我的文件吗？

**不能。** 文件密钥在浏览器端生成，用你的密码哈希加密后才上传。服务器只存储加密后的密钥和加密后的文件，没有你的密码无法解密。

### Q: 忘记密码怎么办？文件会丢吗？

**不会。** 管理员重置密码时，系统会通过 `recovery_key`（用服务器 Master Key 加密的文件密钥副本）还原文件密钥，用新密码重新加密。你的文件可以正常访问。

### Q: 旧手机需要一直开机吗？

**是的。** 作为存储节点的手机需要保持开机和网络连接。App 会以前台服务运行，确保不被系统杀死。

### Q: 存储节点上的文件是加密的吗？

**是的。** 文件通过 AES-256-GCM 加密后写入手机存储，即使手机被取出 SD 卡也无法读取文件内容。

### Q: frp 是什么？为什么需要它？

frp (Fast Reverse Proxy) 是一个内网穿透工具。因为旧手机通常没有公网 IP，frp 通过云服务器的公网 IP 建立反向隧道，让外部请求能访问到内网的存储节点。

### Q: 免费版有什么限制？

- 个人配额 5 GB
- 功能无限制（上传/下载/分享/节点管理均可用）

---

## 未来规划

- [ ] WebSocket 反向通道替代 frp（降低依赖，简化部署）
- [ ] 多文件同时分块上传
- [ ] 文件缩略图 / 在线预览
- [ ] 文件夹打包下载
- [ ] Docker 一键部署
- [ ] Kubernetes 支持
- [ ] 文件去重（相同文件只存一份）
- [ ] P2P 节点间直接传输
- [ ] iOS 存储节点 App

---

## License

MIT License — 详见 [LICENSE](LICENSE)
