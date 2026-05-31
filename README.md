# 家乐云 (JiaLe Cloud)

> 🏠 把旧手机变成你的私人云盘，让闲置设备重获新生。

家乐云是一个**分布式个人云存储系统**，核心思路是——旧安卓手机别扔，装上家乐云 App，立刻变成加密存储节点。多台手机组成你的专属分布式存储网络，数据完全由你掌控。

## ✨ 为什么选择家乐云？

- 🔐 **真正的端到端加密** — 文件密钥在浏览器端生成，AES-256-GCM 加密，服务器永远看不到你的文件内容
- 📱 **旧手机变废为宝** — 闲置安卓手机装 App 即成存储节点，零成本扩容
- 🧩 **分布式存储** — 文件智能分配到不同节点，支持容量负载均衡
- ⚡ **分块上传 + 断点续传** — 大文件自动分块，上传中断可恢复，支持直连节点加速
- 🔗 **文件分享** — 生成加密分享链接，支持过期时间设置
- 🗑️ **回收站保护** — 软删除 + 7 天自动清理，误删可恢复
- 👑 **管理面板** — 用户管理、节点审批、文件浏览一站式管理

## 🏗️ 架构

```
┌──────────────┐      ┌─────────────────────┐      ┌──────────────────┐
│  🖥️ Vue 3 SPA  │◄────►│  ☁️ 协调服务器 (yunpan)  │◄────►│  📱 存储节点 (手机)  │
│  Web 客户端    │      │  Spring Boot • MySQL │      │  安卓 App / Termux │
└──────────────┘      └─────────────────────┘      └──────────────────┘
                              │         ▲
                              ▼         │
                       ┌─────────────────────┐
                       │  💻 存储节点 (Linux)  │
                       │  Spring Boot • frpc  │
                       └─────────────────────┘
```

## 🛠️ 技术栈

| 模块 | 技术 |
|------|------|
| 协调服务器 | Spring Boot 4.0.6 • MyBatis-Plus • MySQL • JWT |
| Web 前端 | Vue 3 • TypeScript • Element Plus • Vite |
| 存储节点(Linux) | Spring Boot • frp 内网穿透 |
| 存储节点(Android) | Java • 内嵌 HTTP Server • ZXing 扫码绑定 |

## 🚀 快速开始

### 1. 配置环境

```bash
cp .env.example .env
# 编辑 .env，填入你的服务器 IP 和数据库密码等配置
```

### 2. 启动协调服务器

```bash
cd yunpan
# 初始化数据库
mysql -u root -p < sql/init.sql
# 启动
mvn spring-boot:run
```

### 3. 启动 Web 前端（开发）

```bash
cd frontend
npm install
npm run dev
```

### 4. 添加存储节点

**Android 手机：** 安装 `Yunpan_App`，打开管理面板扫码绑定。

**Linux/Termux：** 运行一键安装脚本：

```bash
bash <(curl -s http://你的服务器IP:8080/setup.sh) 你的邮箱
```

## 📖 文档

- [CONFIG.md](CONFIG.md) — 全局配置说明
- [yunpan/sql/init.sql](yunpan/sql/init.sql) — 数据库建表脚本

## ⚠️ 安全提示

- `.env` 文件包含敏感信息，**切勿提交到版本控制**（已加入 `.gitignore`）
- 部署前务必修改所有默认密钥（JWT Secret、Master Key、节点 Token）
- 不要使用示例中的密码和密钥用于生产环境

## 📄 License

MIT
