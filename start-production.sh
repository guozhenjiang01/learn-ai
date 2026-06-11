#!/bin/bash
# learn-ai 线上环境启动
set -e
PROJECT_DIR="/home/ubuntu/learn-ai"
LOG_DIR="$PROJECT_DIR/logs-production"
mkdir -p "$LOG_DIR"

LOG_FILE="$LOG_DIR/app_$(date +%Y%m%d_%H%M%S).log"
PID_FILE="$LOG_DIR/app.pid"

echo "🔧 编译打包..."
cd "$PROJECT_DIR"
./mvnw clean package -DskipTests -q

echo "🚀 启动线上环境 (port 8080)..."
nohup java -jar "$PROJECT_DIR/target/learn-ai-0.0.1-SNAPSHOT.jar" \
  --spring.profiles.active=production \
  >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "PID: $(cat $PID_FILE)"
echo "日志: tail -f $LOG_FILE"
echo "地址: http://localhost:8080"
