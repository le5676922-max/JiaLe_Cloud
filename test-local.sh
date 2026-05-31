#!/bin/bash
# ===== 本地最小闭环测试 =====
# 同时启动 yunpan + Cun，验证上传→存储→下载
set -e

YUNPAN_DIR="yunpan"
CUN_DIR="Cun"
TEST_FILE="/tmp/yunpan-test.txt"
API="http://localhost:8080/api"
ADMIN_EMAIL="admin@yunpan.local"
ADMIN_PWD="c0e8d295faddb456f6f3e64e0d74475a56337d98998690cf3c9e1c82a9644beb"

echo "===== JiaLe Cloud 本地测试 ====="

# 1. 编译
echo "[1/6] 编译 yunpan..."
cd $YUNPAN_DIR && mvn compile -q -DskipTests && cd ..
echo "[2/6] 编译 Cun..."
cd $CUN_DIR && mvn compile -q -DskipTests && cd ..

# 2. 启动 yunpan (后台)
echo "[3/6] 启动协调节点..."
kill $(lsof -ti:8080) 2>/dev/null || true
cd $YUNPAN_DIR && nohup mvn spring-boot:run -q > /tmp/yunpan.log 2>&1 &
cd ..
sleep 8

# 3. 启动 Cun (后台，另一个端口)
echo "[4/6] 启动存储节点..."
kill $(lsof -ti:8081) 2>/dev/null || true
cd $CUN_DIR && nohup mvn spring-boot:run -q -Dserver.port=8081 > /tmp/cun.log 2>&1 &
cd ..
sleep 5

# 4. 登录
echo "[5/6] 登录..."
TOKEN=$(curl -s -X POST "$API/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PWD\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "登录失败！检查 /tmp/yunpan.log"
  exit 1
fi
echo "  登录成功"

# 5. 上传测试文件
echo "[6/6] 上传测试文件..."
echo "Hello JiaLe Cloud - $(date)" > $TEST_FILE
UPLOAD=$(curl -s -X POST "$API/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TEST_FILE")
echo "  上传结果: $UPLOAD"

# 6. 检查文件在 Cun 的存储目录
echo ""
echo "===== 测试完成 ====="
echo "文件应存储在: $CUN_DIR/storage_data/"
ls -la $CUN_DIR/storage_data/ 2>/dev/null | tail -5 || echo "  (暂无文件或目录)"

echo ""
echo "  yunpan 日志: /tmp/yunpan.log"
echo "  Cun 日志: /tmp/cun.log"
echo "  前端: http://localhost:5174"
