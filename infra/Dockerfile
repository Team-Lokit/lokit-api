ARG JRE_IMAGE=eclipse-temurin:24-jre-alpine

# Spring Boot layered jar 를 레이어별 디렉터리로 푼다.
# 의존성 레이어는 코드가 바뀌어도 그대로라 레지스트리 push / 서버 pull 에서 재전송되지 않는다.
FROM ${JRE_IMAGE} AS extractor
WORKDIR /extract
COPY app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${JRE_IMAGE}

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apk del tzdata

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# New Relic agent 는 prod 서버에서만 /usr/local/newrelic 에 마운트된다(없으면 agent 없이 기동).
RUN mkdir -p /app/logs /usr/local/newrelic/logs \
    && chown -R spring:spring /app /usr/local/newrelic

# 자주 바뀌지 않는 레이어부터 복사
COPY --chown=spring:spring --from=extractor /extract/extracted/dependencies/ ./
COPY --chown=spring:spring --from=extractor /extract/extracted/spring-boot-loader/ ./
COPY --chown=spring:spring --from=extractor /extract/extracted/snapshot-dependencies/ ./
COPY --chown=spring:spring --from=extractor /extract/extracted/application/ ./

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

# 배포 버전(커밋 SHA). 앱의 배포 완료 알림에 표시된다. 매 빌드 바뀌므로 마지막에 둔다.
ARG APP_VERSION=unknown
ENV APP_VERSION=${APP_VERSION}

ENTRYPOINT ["sh", "-c", "AGENT=''; [ -f /usr/local/newrelic/newrelic.jar ] && AGENT='-javaagent:/usr/local/newrelic/newrelic.jar'; exec java $AGENT $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
