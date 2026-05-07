#!/bin/sh

# Single-RSC Launcher
# Usage: ./run.sh [mode]
# Modes: record (default: just run game)
# When recording, outputs to rsc_recording_YYYYMMDD_HHMMSS.mp4

MODE="${1:-}"

# Get current display (default :0 for local, or use DISPLAY env var)
DISPLAY_NUM="${DISPLAY:-:0}"

if [ "$MODE" = "record" ]; then
    RECORD_FILE="rsc_recording_$(date +%Y%m%d_%H%M%S).mp4"
    echo "[Game] Starting with recording to $RECORD_FILE..."
    ffmpeg -f x11grab -i "$DISPLAY_NUM" -c:v libx264 -preset ultrafast -crf 23 -b:v 2500k "$RECORD_FILE" &
    FFMPEG_PID=$!
fi

java -cp "rsc.jar:lib/*" org.nemotech.rsc.Main

if [ "$MODE" = "record" ]; then
    echo "[Game] Stopping recording..."
    kill $FFMPEG_PID 2>/dev/null
fi