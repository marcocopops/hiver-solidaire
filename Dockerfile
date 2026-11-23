# syntax=docker/dockerfile:1

# --- Build : compilation et extraction du jar -------------------------------------------------
FROM eclipse-temurin:21.0.12.1_1-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Les tests tournent dans la CI (./mvnw verify) : l'image ne les rejoue pas.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar target/app.jar extract --destination /workspace/extracted

# --- Exécution : JRE 21, utilisateur non-root, système de fichiers en lecture seule ---------------
FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
# Correctifs de sécurité Alpine publiés après la construction de l'image de base épinglée (brotli-libs,
# zlib CVE-2026-4738, libexpat…) : on met à jour tous les paquets du système au build. L'image de base reste
# épinglée par empreinte ; le scan de la CI vérifie le résultat. Dependabot remplace l'empreinte chaque semaine.
RUN apk upgrade --no-cache
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=root:root /workspace/extracted/ /app/
# Référence des fichiers de déploiement de cette version (liste blanche de .dockerignore), en lecture seule : update.sh
# les compare à ceux du serveur et signale l'écart, sans rien remplacer (ADR-0015). L'appli ne les lit pas.
COPY --chown=root:root deploy/ /app/deploy/

USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"

# Port de management (8081), jamais routé par le tunnel : il ne sert qu'à cette sonde.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
