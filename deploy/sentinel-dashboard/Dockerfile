# Sentinel Dashboard 自打包镜像（来源完全透明的备选方案）
#
# Sentinel Dashboard 阿里官方只在 GitHub Releases 提供 jar，从不发布官方 Docker 镜像。
# 若 docker-compose.yml 中的社区镜像无法 pull，改用本 Dockerfile 从官方 jar 自行构建：
#
#   cd deploy/sentinel-dashboard
#   docker build -t ypbin/sentinel-dashboard:1.8.8 .
#   # 然后把 docker-compose.yml 里 sentinel-dashboard.image 改为 ypbin/sentinel-dashboard:1.8.8
#
# 构建期从 GitHub 下载官方 jar（需构建机可访问 github.com）。

FROM eclipse-temurin:17-jre

ARG SENTINEL_VERSION=1.8.8
ARG DASHBOARD_JAR_URL=https://github.com/alibaba/Sentinel/releases/download/${SENTINEL_VERSION}/sentinel-dashboard-${SENTINEL_VERSION}.jar

WORKDIR /app
ADD ${DASHBOARD_JAR_URL} /app/sentinel-dashboard.jar

ENV SERVER_PORT=8858

EXPOSE 8858

# 官方 jar 通过系统属性读取端口与账号密码
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${SERVER_PORT} -Dsentinel.dashboard.auth.username=${AUTH_USERNAME} -Dsentinel.dashboard.auth.password=${AUTH_PASSWORD} -jar /app/sentinel-dashboard.jar"]
