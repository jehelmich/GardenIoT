# syntax=docker/dockerfile:1.7
#
# One Dockerfile for every application module; pick the module with a build argument:
#   docker build --build-arg MODULE=controller -t gardeniot/controller .
#
# Stage 1 builds the shaded jar with the Maven wrapper (dependencies cached between builds);
# stage 2 is a small JRE image running as an unprivileged user.

ARG JAVA_VERSION=21

# The build stage runs on the builder's own architecture; jars are portable, so only the small
# runtime stage is built per target platform. That keeps multi-arch builds off QEMU.
FROM --platform=$BUILDPLATFORM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -pl ${MODULE} -am package -DskipTests -Dspotless.check.skip

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine
ARG MODULE
LABEL org.opencontainers.image.source="https://github.com/jehelmich/GardenIoT" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="gardeniot-${MODULE}"
# Pick up Alpine's security patches published since the base image was built, then add a
# numeric UID so that Kubernetes can verify runAsNonRoot without inspecting /etc/passwd.
RUN apk --no-cache upgrade \
    && addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
USER 10001:10001
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/${MODULE}/target/${MODULE}.jar app.jar
# Metrics and health endpoints (see METRICS_PORT); harmless for images that do not serve them.
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "app.jar"]
