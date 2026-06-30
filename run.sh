#!/bin/bash

# 确保脚本在spring-ai目录下运行
cd "$(dirname "$0")"

# 设置类路径
CLASSPATH="./target/classes"

# 添加Maven依赖库
if [ -d "~/.m2/repository" ]; then
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/boot/spring-boot-starter-web/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/boot/spring-boot-starter-data-jpa/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/com/mysql/mysql-connector-j/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/ai/spring-ai-openai-spring-boot-starter/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/ai/spring-ai-ollama-spring-boot-starter/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/boot/spring-boot-starter/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/boot/spring-boot/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/boot/spring-boot-autoconfigure/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/spring-core/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/spring-web/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/spring-webmvc/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/springframework/data/spring-data-jpa/*/*.jar"
    CLASSPATH="$CLASSPATH:~/.m2/repository/org/hibernate/hibernate-core/*/*.jar"
fi

echo "Running Spring AI Demo Application..."
echo "Classpath: $CLASSPATH"

# 运行应用
java -cp "$CLASSPATH" com.example.demo.ChartDemoApplication
