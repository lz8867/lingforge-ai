#!/bin/bash

# 构建项目的脚本
echo "开始构建项目..."

# 确保在正确的目录中
cd "$(dirname "$0")"

# 使用Maven构建项目
echo "执行Maven构建命令..."
mvn clean package -DskipTests

BUILD_RESULT=$?

if [ $BUILD_RESULT -eq 0 ]; then
    echo "构建成功！"
    # 复制构建产物到指定位置
    echo "复制构建产物..."
    cp target/chart-demo-*.jar app.jar
    echo "构建完成，app.jar已更新"
else
    echo "构建失败，退出码: $BUILD_RESULT"
    exit $BUILD_RESULT
fi
