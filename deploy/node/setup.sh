#!/bin/bash
# ===== 一键加入存储网络 =====
# 手机 Termux 粘贴: bash /sdcard/Download/setup.sh
set -e
[ -f .env ] && source .env
CLOUD="${CLOUD_HOST:-your-server-ip}"
DIR=~/yunpan-node
mkdir -p $DIR && cd $DIR
echo "===== JiaLe Cloud 节点安装 ====="

# 1. Java
if ! command -v java &>/dev/null; then
  echo "[1/4] 安装 Java..."
  pkg install -y openjdk-17 wget
fi

# 2. cun.jar (从云端拉取)
if [ ! -f cun.jar ]; then
  echo "[2/4] 下载存储节点..."
  wget -q --show-progress "http://$CLOUD/cun.jar"
fi

# 3. frpc (自动判断架构)
if [ ! -f frpc ]; then
  echo "[3/4] 下载 frpc..."
  ARCH=$(uname -m)
  case $ARCH in aarch64|arm64) FA=arm64 ;; armv7l) FA=arm ;; x86_64) FA=amd64 ;; esac
  wget -q --show-progress "https://github.com/fatedier/frp/releases/download/v0.61.0/frp_0.61.0_linux_${FA}.tar.gz" -O f.tar.gz
  tar -xzf f.tar.gz && cp frp_0.61.0_linux_${FA}/frpc . && rm -rf frp_* f.tar.gz
  chmod +x frpc
fi

# 4. 生成配置 + 启动
echo "[4/4] 启动..."
NID="node-$(date +%s | tail -c 5)"
PORT=$((18080 + RANDOM % 100))

cat > frpc.ini << EOF
[common]
server_addr = $CLOUD
server_port = 443
[$NID]
type = tcp
local_ip = 127.0.0.1
local_port = 8080
remote_port = $PORT
EOF

cat > application.yml << EOF
server:
  port: 8080
  tomcat:
    threads:
      max: 30
      min-spare: 5
spring:
  datasource:
    url: jdbc:mysql://${CLOUD}:13306/yunpan
    username: root
    password: ${DB_PASSWORD:-1234}
    driver-class-name: com.mysql.cj.jdbc.Driver
mybatis-plus:
  type-aliases-package: org.example.yunpan.entity
  configuration:
    map-underscore-to-camel-case: true
yunpan:
  cloud:
    host: ${CLOUD}
  node:
    id: ${NID}
    name: ${NID}
    frp-port: ${PORT}
    token: ${NODE_TOKEN:-change-me-node-token}
  jwt:
    secret: ${JWT_SECRET:-change-me-jwt-secret}
  storage:
    root-path: ./storage_data
  membership:
    free-quota: 5368709120
    vip-quota: 32212254720
  compression:
    enabled: true
    level: 6
    skip-extensions: jpg,jpeg,png,gif,webp,heic,avif,mp4,mkv,avi,mov,webm,flv,wmv,mp3,aac,ogg,wav,flac,zip,rar,7z,gz,bz2,xz
    min-size: 1024
  upload:
    chunk-size: 10485760
    chunk-parallel: 3
    chunk-ttl-hours: 24
  security:
    master-key: ${MASTER_KEY:-change-me-to-a-64-char-hex-string}
EOF

mkdir -p storage_data
kill $(pgrep -f "frpc.*frpc.ini") 2>/dev/null || true
kill $(pgrep -f "cun.jar") 2>/dev/null || true
sleep 1
nohup ./frpc -c frpc.ini > frpc.log 2>&1 &
sleep 2
nohup java -Xmx256m -Xms128m -jar cun.jar > cun.log 2>&1 &

# 看门狗
cat > ~/yunpan-node/wd.sh << 'WDOG'
while true; do
  pgrep -f "frpc.*frpc.ini" >/dev/null || { cd ~/yunpan-node && nohup ./frpc -c frpc.ini > frpc.log 2>&1 & }
  pgrep -f "cun.jar" >/dev/null || { cd ~/yunpan-node && nohup java -Xmx256m -jar cun.jar > cun.log 2>&1 & }
  sleep 30
done
WDOG
chmod +x ~/yunpan-node/wd.sh
nohup bash ~/yunpan-node/wd.sh > ~/yunpan-node/wd.log 2>&1 &

sleep 2
echo "===== 完成 ====="
echo "节点: $NID  端口: $PORT"
echo "去 http://$CLOUD/join 查看下一步"
