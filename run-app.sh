#!/bin/bash

# 确保脚本在spring-ai目录下运行
cd "$(dirname "$0")"

echo "Running Spring AI Demo Application..."
echo "Using database URL: jdbc:mysql://localhost:3306/activiti"

# 设置环境变量
export MYSQL_PASSWORD=activiti

# 尝试使用Spring Boot的方式运行应用
if [ -f "pom.xml" ]; then
    echo "Found pom.xml, trying to run with Maven..."
    # 尝试使用Maven运行
    mvn spring-boot:run 2>/dev/null || echo "Maven not available, trying alternative..."
fi

# 如果Maven不可用，尝试直接运行编译后的类
echo "Trying to run compiled classes..."
# 简单的类路径设置
CLASSPATH="./target/classes"

# 运行应用
echo "Attempting to run application..."
java -cp "$CLASSPATH" com.example.demo.ChartDemoApplication || {
    echo "Error running application: $!"
    echo "Please ensure all dependencies are available."
    exit 1
}
