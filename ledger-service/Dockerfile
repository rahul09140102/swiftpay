# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy parent POM + common module first (for layer caching)
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src     common/src

ARG SERVICE_NAME
COPY ${SERVICE_NAME}/pom.xml ${SERVICE_NAME}/pom.xml
COPY ${SERVICE_NAME}/src     ${SERVICE_NAME}/src

# Resolve deps offline (cache-friendly), then build without tests
RUN mvn -pl common install -DskipTests -q
RUN mvn -pl ${SERVICE_NAME} package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S swiftpay && adduser -S swiftpay -G swiftpay

ARG SERVICE_NAME
ARG JAR_FILE=/workspace/${SERVICE_NAME}/target/*.jar
COPY --from=builder ${JAR_FILE} app.jar

RUN chown swiftpay:swiftpay app.jar
USER swiftpay

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
