# ---- Build stage ----
# Use --platform to ensure consistent builds across architectures
FROM --platform=linux/amd64 maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Leverage build cache for dependencies
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -q -Dmaven.test.skip=true package

# ---- Runtime stage ----
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine

# Install runtime dependencies (curl for healthcheck, ffmpeg for audio extraction,
# su-exec for dropping privileges in entrypoint)
# Install yt-dlp via pip with curl_cffi for TikTok browser impersonation support
RUN apk add --no-cache curl ffmpeg python3 py3-pip su-exec \
  && pip3 install --break-system-packages "yt-dlp[curl-cffi]"

# Create a non-root user
RUN addgroup -S app && adduser -S app -G app \
  && mkdir -p /app/logs \
  && chown -R app:app /app

# Workdir
WORKDIR /app

# Copy the fat jar and entrypoint
COPY --from=builder /workspace/target/*.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Default envs (can be overridden at runtime)
# Map env to Spring properties via relaxed binding
ENV JAVA_OPTS="-XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV APP_FFMPEG_LOCATION=/usr/bin/ffmpeg

# Expose application port
EXPOSE 8081
# Healthcheck using Actuator readiness endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1

# Run as root so entrypoint can update yt-dlp, then drops to 'app' user via su-exec
ENTRYPOINT ["/app/entrypoint.sh"]
