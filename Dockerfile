FROM eclipse-temurin:17-jdk

WORKDIR /app

RUN apt-get update \
    && apt-get install -y bash coreutils curl docker.io docker-compose python3 \
    && rm -rf /var/lib/apt/lists/*

COPY app.jar app.jar
COPY deploy-background.sh deploy-background.sh
RUN chmod +x deploy-background.sh

EXPOSE 8080
ENV DEPLOY_PROJECT_DIR=/workspace

ENTRYPOINT ["java", "-jar", "app.jar"]
