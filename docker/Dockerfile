FROM eclipse-temurin:8-jre-alpine

LABEL maintainer="x-infra-lab"
LABEL description="X-API-Gateway"

RUN addgroup -S gateway && adduser -S gateway -G gateway

WORKDIR /app

COPY x-api-gateway-dist/target/x-api-gateway-dist-*.jar /app/gateway.jar
COPY docker/config.yml /app/config.yml
COPY docker/logback.xml /app/logback.xml

RUN mkdir -p /app/logs && chown -R gateway:gateway /app

USER gateway

EXPOSE 8080 9090

ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+AlwaysPreTouch \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/heapdump.hprof \
    -Dio.netty.leakDetection.level=disabled \
    -Dlogback.configurationFile=/app/logback.xml"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/gateway.jar -c /app/config.yml"]
