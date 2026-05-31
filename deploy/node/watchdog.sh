#!/bin/bash
# ===== 存储节点看门狗 =====
# 每隔 30s 检查 frpc 和 cun 是否存活，挂了就拉起来

check_and_restart() {
  if ! pgrep -f "frpc.*frpc.ini" > /dev/null; then
    echo "$(date): frpc 已停止，重新启动..."
    cd ~/yunpan-node && nohup ./frpc -c frpc.ini > frpc.log 2>&1 &
  fi
  if ! pgrep -f "cun.jar" > /dev/null; then
    echo "$(date): cun 已停止，重新启动..."
    cd ~/yunpan-node && nohup java -Xmx256m -Xms128m -jar cun.jar > cun.log 2>&1 &
  fi
}

while true; do
  check_and_restart
  sleep 30
done
