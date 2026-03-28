#!/bin/sh
set -e

echo "Updating yt-dlp..."
if pip3 install --break-system-packages --upgrade --quiet "yt-dlp[curl-cffi]" 2>/dev/null; then
  echo "yt-dlp updated to $(yt-dlp --version)"
else
  echo "WARNING: yt-dlp update failed, continuing with existing version: $(yt-dlp --version 2>/dev/null || echo 'unknown')"
fi

echo "Starting application..."
exec su-exec app java $JAVA_OPTS -jar /app/app.jar
