#!/bin/bash

GATEWAY_HOME="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$GATEWAY_HOME/gateway.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Gateway is not running (no pid file found)"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo "Gateway process $PID is not running, removing stale pid file"
    rm -f "$PID_FILE"
    exit 0
fi

echo "Stopping Gateway (pid=$PID)..."
kill "$PID"

TIMEOUT=30
ELAPSED=0
while kill -0 "$PID" 2>/dev/null; do
    if [ $ELAPSED -ge $TIMEOUT ]; then
        echo "Gateway did not stop within ${TIMEOUT}s, force killing..."
        kill -9 "$PID"
        break
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

rm -f "$PID_FILE"
echo "Gateway stopped."
