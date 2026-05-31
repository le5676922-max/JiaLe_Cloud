#!/bin/bash
# ===== JiaLe Cloud 健康检查 =====
# crontab: */5 * * * * bash /opt/yunpan/health-check.sh
# 每5分钟检查一次，挂了就重启

API="http://localhost:8080/api/auth/login"
YUNPAN_DIR="/opt/yunpan"
FRP_DIR="/opt/frp"
LOG="/tmp/yunpan-health.log"

check() {
  # 1. 后端是否响应
  if ! curl -s --max-time 5 "$API" -X POST -H "Content-Type: application/json" \
    -d '{"email":"test","password":"test"}' | grep -q "401"; then
    echo "$(date) BACKEND DOWN - restarting" >> $LOG
    kill $(pgrep -f yunpan.jar) 2>/dev/null
    sleep 2
    cd $YUNPAN_DIR && nohup java -Xmx256m -jar yunpan.jar > yunpan.log 2>&1 &
    return
  fi

  # 2. frp 是否在跑
  if ! pgrep -f "frps -c frps.ini" > /dev/null; then
    echo "$(date) FRPS DOWN - restarting" >> $LOG
    cd $FRP_DIR && nohup ./frps -c frps.ini > frps.log 2>&1 &
  fi

  if ! pgrep -f "frpc-mysql" > /dev/null; then
    echo "$(date) FRPC-MYSQL DOWN - restarting" >> $LOG
    cd $FRP_DIR && nohup ./frpc -c frpc-mysql.ini > frpc-mysql.log 2>&1 &
  fi
}

check
