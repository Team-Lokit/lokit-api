ARG JRE_IMAGE=eclipse-temurin:24-jre-alpine

FROM ${JRE_IMAGE}

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apk del tzdata

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --chown=spring:spring ./newrelic/newrelic.jar /usr/local/newrelic/newrelic.jar
COPY --chown=spring:spring ./newrelic/newrelic.yml /usr/local/newrelic/newrelic.yml
RUN mkdir -p /usr/local/newrelic/logs \
    && chown -R spring:spring /usr/local/newrelic

RUN mkdir -p /app/logs && chown -R spring:spring /app
COPY --chown=spring:spring app.jar app.jar

USER spring

EXPOSE 8080

ENV NEW_RELIC_LOG_FILE_NAME=STDOUT
ENV NEW_RELIC_LOG_LEVEL=info

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=70.0 \
    -Xss512k \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+ExitOnOutOfMemoryError \
    --enable-preview \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java -javaagent:/usr/local/newrelic/newrelic.jar $JAVA_OPTS -jar app.jar"]
