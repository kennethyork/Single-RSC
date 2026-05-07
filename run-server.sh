#!/bin/bash

# Single-RSC 24/7 Server Script
# Usage: ./run-server.sh [botname]
# Bot names: woodcut, mine, fish, combat, cook, smith, fire, prayer, agility, herb, thieve, fletch, range, magic

DISPLAY_NUM=99
STREAM_KEY="${STREAM_KEY:-}"
BOT_NAME="${1:-woodcut}"

XVFB_RESOLUTION="1024x768x16"

cleanup() {
    echo "[Server] Shutting down..."
    kill $XVFB_PID 2>/dev/null
    kill $GAME_PID 2>/dev/null
    exit 0
}

trap cleanup SIGINT SIGTERM

echo "[Server] Starting Xvfb on display :$DISPLAY_NUM..."
Xvfb :$DISPLAY_NUM -screen 0 $XVFB_RESOLUTION &
XVFB_PID=$!
sleep 2

export DISPLAY=:$DISPLAY_NUM

echo "[Server] Starting Single-RSC..."
java -jar rsc.jar &
GAME_PID=$!

sleep 5

echo "[Server] Waiting for game to load..."
sleep 10

echo "[Server] Starting bot: $BOT_NAME"

case "$BOT_NAME" in
    woodcut)  BOT_CMD="::wc" ;;
    mine)    BOT_CMD="::mine" ;;
    fish)    BOT_CMD="::fish" ;;
    combat)  BOT_CMD="::combat" ;;
    cook)    BOT_CMD="::cook" ;;
    smith)   BOT_CMD="::smith" ;;
    fire)    BOT_CMD="::fire" ;;
    prayer)  BOT_CMD="::prayer" ;;
    agility) BOT_CMD="::agility" ;;
    herb)    BOT_CMD="::herb" ;;
    thieve)  BOT_CMD="::thieve" ;;
    fletch)  BOT_CMD="::fletch" ;;
    range)   BOT_CMD="::range" ;;
    magic)   BOT_CMD="::magic" ;;
    *)       BOT_CMD="::wc" ;;
esac

# Send bot command to game
echo "$BOT_CMD" | nc -w 1 localhost 43594 2>/dev/null || echo "[Server] Note: nc not available, start bot manually with $BOT_CMD"

if [ -n "$STREAM_KEY" ]; then
    echo "[Server] Starting stream to YouTube..."
    ffmpeg -f x11grab -i :$DISPLAY_NUM -c:v libx264 -preset ultrafast -b:v 2500k -maxrate 2500k -bufsize 5000k -g 60 -f flv "rtmp://a.rtmp.youtube.com/live2/$STREAM_KEY" &
    STREAM_PID=$!
fi

RESTART_COUNT=0
MAX_RESTARTS=10

while true; do
    if ! kill -0 $GAME_PID 2>/dev/null; then
        RESTART_COUNT=$((RESTART_COUNT + 1))
        if [ $RESTART_COUNT -gt $MAX_RESTARTS ]; then
            echo "[Server] Too many restarts ($RESTART_COUNT), giving up."
            break
        fi
        echo "[Server] Game crashed! Restarting... ($RESTART_COUNT/$MAX_RESTARTS)"
        sleep 5
        java -jar rsc.jar &
        GAME_PID=$!
        sleep 10
    fi
    sleep 30
done

cleanup
