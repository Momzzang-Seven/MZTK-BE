# syntax=docker/dockerfile:1

# ============================================
# Build Stage
# ============================================
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar --no-daemon

# ============================================
# Runtime Stage
# ============================================
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine
WORKDIR /app

# 타임존 설정
RUN apk add --no-cache tzdata wget
ENV TZ=Asia/Seoul

# 보안: 일반 사용자로 실행
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
  "-Xms256m", \
  "-Xmx512m", \
  "-jar", \
  "app.jar"]
