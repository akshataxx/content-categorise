# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-24 AS builder
WORKDIR /workspace

# Leverage build cache for dependencies
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:24-jre-alpine

# Install runtime dependencies (curl for healthcheck, ffmpeg for app functionality)
RUN apk add --no-cache curl ffmpeg yt-dlp

# Create a non-root user
RUN addgroup -S app && adduser -S app -G app \
  && mkdir -p /app/logs \
  && chown -R app:app /app
USER app

# Workdir
WORKDIR /app

# Copy the fat jar
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Default envs (can be overridden at runtime)
# Map env to Spring properties via relaxed binding
ENV JAVA_OPTS="-XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV APP_FFMPEG_LOCATION=/usr/bin/ffmpeg

# Expose application port
EXPOSE 8080

# Healthcheck using Actuator readiness endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
