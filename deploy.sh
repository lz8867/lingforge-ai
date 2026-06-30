#!/bin/bash

# 部署脚本，在容器外部执行部署操作

echo "开始部署..."

# 停止并删除现有容器
echo "停止旧容器..."
docker stop spring-ai-app || true

echo "移除旧容器..."
docker rm spring-ai-app || true

# 启动新容器
echo "启动服务..."
cd "$(dirname "$0")"
docker-compose up -d

# 检查容器状态
sleep 2
echo "检查容器状态..."
docker ps | grep spring-ai-app

if [ $? -eq 0 ]; then
    echo "部署成功！"
    exit 0
else
    echo "部署失败！"
    exit 1
fi
