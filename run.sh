#!/bin/sh

# Single-RSC Launcher with Recording
# Usage: ./run.sh [mode]
# Modes: record - record just the game window (no audio)
# Outputs: rsc_recording_YYYYMMDD_HHMMSS.mp4 (video only)

MODE="${1:-}"

if [ "$MODE" = "record" ]; then
    RECORD_FILE="rsc_recording_$(date +%Y%m%d_%H%M%S).mp4"
    echo "[Game] Starting in record mode..."
    echo "[Game] Output: $RECORD_FILE (no audio)"
    echo "[Game] Launching game..."

    # Launch game
    java -cp "rsc.jar:lib/*" org.nemotech.rsc.Main &

    # Wait for game to load
    sleep 6

    echo "[Game] Starting recording (video only)..."

    # Record full screen at game resolution (RSC is typically 4:3 around 800x600)
    ffmpeg -f x11grab -i :0 \
        -vf "crop=800:600:0:0" \
        -c:v libx264 -preset ultrafast -crf 23 -b:v 2500k \
        -r 30 -an "$RECORD_FILE"

else
    java -cp "rsc.jar:lib/*" org.nemotech.rsc.Main
fi