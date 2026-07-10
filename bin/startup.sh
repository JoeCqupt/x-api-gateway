#!/bin/bash

GATEWAY_HOME="$(cd "$(dirname "$0")/.." && pwd)"
GATEWAY_JAR="$GATEWAY_HOME/lib/gateway.jar"

if [ ! -f "$GATEWAY_JAR" ]; then
    GATEWAY_JAR=$(ls "$GATEWAY_HOME"/x-api-gateway-dist/target/x-api-gateway-dist-*.jar 2>/dev/null | head -1)
fi

if [ ! -f "$GATEWAY_JAR" ]; then
    echo "Error: gateway jar not found. Run 'mvn package -DskipTests' first."
    exit 1
fi

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m -XX:+UseG1GC -XX:+AlwaysPreTouch}"
CONFIG_FILE="${CONFIG_FILE:-$GATEWAY_HOME/conf/config.yml}"
LOG_CONFIG="${LOG_CONFIG:-$GATEWAY_HOME/conf/logback.xml}"
LOG_DIR="${LOG_DIR:-$GATEWAY_HOME/logs}"
PID_FILE="$GATEWAY_HOME/gateway.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Gateway is already running (pid=$PID)"
        exit 1
    fi
    rm -f "$PID_FILE"
fi

mkdir -p "$LOG_DIR"

if [ -f "$LOG_CONFIG" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlogback.configurationFile=$LOG_CONFIG"
fi

CONFIG_ARGS=""
if [ -f "$CONFIG_FILE" ]; then
    CONFIG_ARGS="-c $CONFIG_FILE"
fi

echo "Starting X-API-Gateway..."
echo "  GATEWAY_JAR: $GATEWAY_JAR"
echo "  CONFIG_FILE: $CONFIG_FILE"
echo "  LOG_DIR:     $LOG_DIR"

nohup java $JAVA_OPTS -jar "$GATEWAY_JAR" $CONFIG_ARGS \
    > "$LOG_DIR/stdout.log" 2>&1 &

echo $! > "$PID_FILE"
echo "Gateway started (pid=$(cat "$PID_FILE"))"
