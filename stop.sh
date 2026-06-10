#!/bin/bash
# 停止指定环境
# 用法: ./stop.sh staging  或  ./stop.sh production
ENV=$1
if [ -z "$ENV" ]; then
  echo "用法: $0 <staging|production>"
  exit 1
fi

PROJECT_DIR="/home/ubuntu/learn-ai"
PID_FILE="$PROJECT_DIR/logs-$ENV/app.pid"

if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo "🛑 停止 $ENV 环境 (PID: $PID)..."
    kill "$PID"
    sleep 2
    if kill -0 "$PID" 2>/dev/null; then
      echo "强制终止..."
      kill -9 "$PID"
    fi
    echo "✅ 已停止"
  else
    echo "⚠️  $ENV 环境未运行"
  fi
  rm -f "$PID_FILE"
else
  echo "⚠️  $ENV 环境未运行 (无 PID 文件)"
fi
