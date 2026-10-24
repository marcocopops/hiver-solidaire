# syntax=docker/dockerfile:1

# --- Build : compilation et extraction du jar -------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Les tests tournent dans la CI (./mvnw verify) : l'image ne les rejoue pas.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar target/app.jar extract --destination /workspace/extracted

# --- Exécution : JRE 21, utilisateur non-root, système de fichiers en lecture seule ---------------
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=root:root /workspace/extracted/ /app/

USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"

# Port de management (8081), jamais routé par le tunnel : il ne sert qu'à cette sonde.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
