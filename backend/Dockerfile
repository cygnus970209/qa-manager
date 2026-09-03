# syntax=docker/dockerfile:1.7
# ────────── Stage 1: build ──────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Gradle wrapper / 설정만 먼저 복사해서 의존성 캐싱
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 실제 소스
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ────────── Stage 2: runtime ──────────
FROM eclipse-temurin:25-jre
WORKDIR /app

# 헬스체크(docker-compose healthcheck: wget --spider /actuator/health)용 — temurin JRE 이미지에는 wget/curl 이 없다.
# 이게 없으면 컨테이너가 영원히 unhealthy 라 deploy.sh(무중단 배포)가 새 색을 띄우지 못한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# 실행용 비루트 유저
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom --enable-native-access=ALL-UNNAMED" \
    TZ="Asia/Seoul"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
