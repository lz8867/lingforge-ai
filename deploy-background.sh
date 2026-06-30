#!/bin/bash

# 重定向所有输出到日志文件
LOG_FILE="/app/deploy-full.log"
touch $LOG_FILE
exec > >(tee -a $LOG_FILE) 2>&1

echo "$(date) - 开始执行后台部署操作..."

# 1. 检查Docker是否可用
if ! docker info > /dev/null 2>&1; then
    echo "$(date) - 错误：Docker 不可用，请检查Docker服务是否运行"
    exit 1
fi

# 2. 检查MySQL容器是否已启动
if [ "$(docker ps -q -f name=mysql)" ]; then
    echo "$(date) - MySQL容器已经存在，跳过启动步骤"
else
    echo "$(date) - 启动MySQL容器..."
    docker run -d --restart=always --name mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=activiti -e MYSQL_USER=activiti -e MYSQL_PASSWORD=activiti -p 3306:3306 mysql:8.0

    if [ $? -ne 0 ]; then
        echo "$(date) - 错误：启动MySQL容器失败"
        exit 1
    fi

    # 等待MySQL容器启动完成
    echo "$(date) - 等待MySQL容器启动完成..."
    for i in {1..30}; do
        if docker logs mysql 2>&1 | grep "ready for connections" > /dev/null; then
            echo "$(date) - MySQL容器已启动完成"
            break
        fi
        echo "$(date) - 等待MySQL容器启动... ($i/30)"
        sleep 2
    done
fi

# 3. 检查MySQL容器是否运行
if ! docker ps -q -f name=mysql > /dev/null; then
    echo "$(date) - 错误：MySQL容器未运行"
    exit 1
fi

# 4. 构建新的镜像
echo "$(date) - 构建新的应用镜像..."
docker build -t chart-demo:latest .

if [ $? -ne 0 ]; then
    echo "$(date) - 错误：构建应用镜像失败"
    exit 1
fi

# 5. 停止并移除旧的应用容器（如果存在）
echo "$(date) - 停止并移除旧的应用容器..."
docker stop chart-demo || true
docker rm chart-demo || true

# 6. 启动新的应用容器
echo "$(date) - 启动新的应用容器..."
docker run -d --restart=always --name chart-demo -p 8081:8080 -v /var/run/docker.sock:/var/run/docker.sock --link mysql:mysql -e MYSQL_PASSWORD=activiti -e SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/activiti?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" chart-demo:latest

if [ $? -ne 0 ]; then
    echo "$(date) - 错误：启动应用容器失败"
    exit 1
fi

# 7. 等待应用容器启动成功
echo "$(date) - 等待应用容器启动成功..."
for i in {1..20}; do
    if docker ps -q -f name=chart-demo > /dev/null; then
        echo "$(date) - 应用容器已启动"
        # 检查应用是否正常运行
        sleep 5
        if curl -s http://localhost:8081 > /dev/null; then
            echo "$(date) - 应用已正常运行！"
            echo "$(date) - 后台部署操作已完成！"
            echo "$(date) - 应用已成功部署并启动。"
            echo "$(date) - 可以通过 http://localhost:8081 访问应用。"
            exit 0
        else
            echo "$(date) - 应用容器已启动，但可能尚未完全就绪..."
        fi
    else
        echo "$(date) - 应用容器尚未启动，重新启动..."
        docker start chart-demo || docker run -d --restart=always --name chart-demo -p 8081:8080 -v /var/run/docker.sock:/var/run/docker.sock --link mysql:mysql -e MYSQL_PASSWORD=activiti -e SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/activiti?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" chart-demo:latest
    fi
    echo "$(date) - 等待应用容器启动... ($i/20)"
    sleep 3
done

echo "$(date) - 错误：应用容器启动失败或无法正常访问"
echo "$(date) - 检查Docker日志获取更多信息："
docker logs chart-demo || true
exit 1
