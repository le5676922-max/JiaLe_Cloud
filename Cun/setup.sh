#!/bin/bash
# ===== 存储节点一键部署 =====
# 手机 Termux: bash /sdcard/Download/setup.sh [你的邮箱]
set -e
[ -f .env ] && source .env
CLOUD="${CLOUD_HOST:-your-server-ip}"
OWNER="${1:-}"
DIR=~/cun-node

echo "===== 存储节点安装 ====="
mkdir -p $DIR && cd $DIR

# 1. Java
command -v java &>/dev/null || { echo "[1/4] 装 Java..."; pkg install -y openjdk-17 wget; }

# 2. JAR
if [ ! -f cun.jar ]; then
  echo "[2/4] 下载 JAR..."
  wget -q "http://$CLOUD/cun.jar" && echo "  下载完成"
fi

# 3. frpc
if [ ! -f frpc ]; then
  echo "[3/4] 下载 frpc..."
  ARCH=$(uname -m)
  case $ARCH in aarch64|arm64) FA=arm64 ;; armv7l) FA=arm ;; x86_64) FA=amd64 ;; esac
  wget -q "https://github.com/fatedier/frp/releases/download/v0.61.0/frp_0.61.0_linux_${FA}.tar.gz" -O f.tar.gz && echo "  下载完成"
  tar -xzf f.tar.gz && cp frp_0.61.0_linux_${FA}/frpc . && rm -rf frp_* f.tar.gz && chmod +x frpc
fi

# 4. 启动
NID="node-$(date +%s|tail -c5)"
PORT=$((18080+RANDOM%100))
echo "[4/4] 启动... 节点: $NID 端口: $PORT"

cat > frpc.ini << EOF
[common]
server_addr = $CLOUD
server_port = 443
protocol = kcp
tcp_mux = false
[$NID]
type = tcp
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
yunpan:
  cloud:
    host: $CLOUD
  node:
    id: $NID
    frp-port: $PORT
    owner: ${OWNER}
    token: ${NODE_TOKEN:-change-me-node-token}
  jwt:
    secret: ${JWT_SECRET:-change-me-jwt-secret}
  storage:
    root-path: ./storage_data
  security:
    master-key: ${MASTER_KEY:-change-me-to-a-64-char-hex-string}
  compression:
    enabled: true
    level: 6
    skip-extensions: jpg,jpeg,png,gif,webp,heic,avif,mp4,mkv,avi,mov,webm,flv,wmv,mp3,aac,ogg,wav,flac,zip,rar,7z,gz,bz2,xz
    min-size: 1024
EOF

mkdir -p storage_data
kill $(pgrep -f "frpc.*frpc.ini") 2>/dev/null; kill $(pgrep -f "cun.jar") 2>/dev/null; sleep 1
nohup ./frpc -c frpc.ini > frpc.log 2>&1 &
sleep 2
nohup java -Xmx256m -jar cun.jar > cun.log 2>&1 &

# 看门狗
cat > wd.sh << 'WDOG'
while true; do
  pgrep -f "frpc.*frpc.ini" >/dev/null || { cd ~/cun-node && nohup ./frpc -c frpc.ini > frpc.log 2>&1 & }
  pgrep -f "cun.jar" >/dev/null || { cd ~/cun-node && nohup java -Xmx256m -jar cun.jar > cun.log 2>&1 & }
  sleep 30
done
WDOG
chmod +x wd.sh && nohup bash wd.sh > wd.log 2>&1 &

echo "===== 完成 ====="
echo "节点: $NID  端口: $PORT"
echo "去 http://$CLOUD/admin 批准节点"
