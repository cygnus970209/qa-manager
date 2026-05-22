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

# 실행용 비루트 유저
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown app:app /app/app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
