#!/bin/bash
# Simple watchdog - monitors and restarts Single-RSC
# Just run: ./watchdog.sh

DISPLAY_NUM=99
BOT="${1:-woodcut}"

check_and_restart() {
    if ! pgrep -f "rsc.jar" > /dev/null; then
        echo "[Watchdog] $(date) - Game not running, starting..."
        DISPLAY=:$DISPLAY_NUM java -jar rsc.jar > /dev/null 2>&1 &
        sleep 15
    fi
}

echo "[Watchdog] Starting Single-RSC watchdog..."
echo "[Watchdog] Bot: $BOT"
echo "[Watchdog] Press Ctrl+C to stop"

Xvfb :$DISPLAY_NUM -screen 0 1024x768x16 > /dev/null 2>&1 &
XVFB_PID=$!
sleep 2

while true; do
    check_and_restart
    sleep 60
done

kill $XVFB_PID 2>/dev/null
