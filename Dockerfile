# Multi-stage build — Stage 1 compiles the app, Stage 2 runs it.
# Keeping them separate means the final image contains only the JRE and the jar,
# not the JDK, Gradle, or source code (~150MB vs ~500MB+).

# ── Stage 1: Build ───────────────────────────────────────────────────────────

# Base image: Alpine Linux + JDK 21. Alpine is a minimal Linux distro (~5MB).
# AS build names this stage so Stage 2 can reference it with --from=build.
FROM eclipse-temurin:21-jdk-alpine AS build

# All subsequent commands run inside /app inside the container filesystem.
WORKDIR /app

# Copy build files before source code. Docker caches each layer — if these
# files haven't changed, Docker reuses the cached layer and skips re-downloading
# Gradle dependencies on the next build. Source changes don't bust this cache.
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Copy source code last — changes here only invalidate layers from this line on.
COPY src ./src

# Ensure the Gradle wrapper script is executable. On some systems (especially
# after cloning on macOS) the file permission bits are stripped.
RUN chmod +x gradlew

# Compile and package into a fat jar. -x test skips tests to keep the build fast.
# The jar lands in build/libs/ — Spring Boot's bootJar task bundles all dependencies.
RUN ./gradlew bootJar -x test

# ── Stage 2: Run ─────────────────────────────────────────────────────────────

# Fresh base image — JRE only, no compiler. Nothing from Stage 1 carries over
# except what we explicitly copy. This is what gets deployed.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Pull only the compiled jar from Stage 1. Everything else (source, Gradle cache,
# JDK) is discarded — it never enters this image.
COPY --from=build /app/build/libs/*.jar app.jar

# Create a non-root user and switch to it. If the container is ever compromised,
# the attacker runs as appuser, not root — limiting what they can do on the host.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Documents that the app listens on 8080. Does NOT open the port — that happens
# at docker run (-p 8080:8080) or in Docker Compose under ports:.
EXPOSE 8080

# ENTRYPOINT makes the container behave like an executable. Unlike CMD, it cannot
# be accidentally overridden by arguments passed to docker run.
ENTRYPOINT ["java", "-jar", "app.jar"]
