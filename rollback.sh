#!/bin/bash
# 回滚脚本 — 回滚到指定 git tag 并重新部署
# 用法: ./rollback.sh v1.0 [环境: staging|production|both]

TAG="${1:-}"
ENV="${2:-staging}"

if [ -z "$TAG" ]; then
    echo "用法: ./rollback.sh <tag> [staging|production|both]"
    echo "可用标签:"
    git tag -l
    exit 1
fi

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROD_DIR="/home/ubuntu/learn-ai-prod"

echo "============================================"
echo "  回滚到 $TAG  |  环境: $ENV"
echo "============================================"

# 回滚测试环境
rollback_staging() {
    echo ""
    echo ">>> 回滚测试环境..."
    cd "$PROJECT_DIR"
    
    # 备份当前状态
    git stash 2>/dev/null
    
    # 检出标签
    git checkout "$TAG" 2>/dev/null || { echo "❌ 标签 $TAG 不存在"; exit 1; }
    
    # 杀掉旧进程
    kill $(ss -tlnp | grep ':8080' | grep -oP 'pid=\K\d+') 2>/dev/null
    sleep 1
    
    # 重建启动
    bash start-staging.sh
    echo "✅ 测试环境回滚完成 (8080)"
}

# 回滚线上环境
rollback_production() {
    echo ""
    echo ">>> 回滚线上环境..."
    cd "$PROJECT_DIR"
    git checkout "$TAG" 2>/dev/null
    
    # 备份线上配置
    cp "$PROD_DIR"/src/main/resources/application-production.yml /tmp/app-prod.yml.bak 2>/dev/null
    cp "$PROD_DIR"/start-production.sh /tmp/start-prod.sh.bak 2>/dev/null
    
    # 同步
    rsync -a --delete \
      --exclude='start-staging.sh' --exclude='start-production.sh' \
      --exclude='src/main/resources/application-staging.yml' \
      --exclude='src/main/resources/application-production.yml' \
      --exclude='logs-staging' --exclude='logs-production' \
      --exclude='uploads-staging' --exclude='uploads-production' \
      --exclude='target' \
      "$PROJECT_DIR/" "$PROD_DIR/"
    
    # 恢复线上配置
    cp /tmp/app-prod.yml.bak "$PROD_DIR"/src/main/resources/application-production.yml 2>/dev/null
    cp /tmp/start-prod.sh.bak "$PROD_DIR"/start-production.sh 2>/dev/null
    
    # 设置端口颜色
    sed -i "s/let PORT = [0-9]*/let PORT = 8081/" "$PROD_DIR"/src/main/resources/static/index.html
    sed -i "s/footerPort'>[0-9]*/footerPort'>8081/" "$PROD_DIR"/src/main/resources/static/index.html
    
    # 重启
    kill $(ss -tlnp | grep ':8081' | grep -oP 'pid=\K\d+') 2>/dev/null
    sleep 1
    cd "$PROD_DIR" && bash start-production.sh
    echo "✅ 线上环境回滚完成 (8081)"
}

case "$ENV" in
    staging)  rollback_staging ;;
    production) rollback_production ;;
    both)
        rollback_staging
        rollback_production
        ;;
    *) echo "未知环境: $ENV (可选: staging, production, both)" ;;
esac

echo ""
echo "============================================"
echo "  回滚完成！当前版本: $(git describe --tags 2>/dev/null || echo $TAG)"
echo "============================================"
