FROM eclipse-temurin:24-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon bootJar -x test \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" app.jar

FROM eclipse-temurin:24-jre-alpine

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apk del tzdata

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

RUN mkdir -p /app/logs \
    && chown -R spring:spring /app

COPY --from=builder --chown=spring:spring /workspace/app.jar app.jar

USER spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -Xss512k \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+ExitOnOutOfMemoryError \
    --enable-preview \
    -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
