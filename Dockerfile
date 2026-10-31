# ==============================================================================
# Multi-stage Dockerfile for Spring Boot File_Sharing Application
# Optimized for Render deployment (Dynamic $PORT, container memory tuning, caching)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build the Application
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first to cache Maven dependency downloads
COPY pom.xml .

# Download dependencies in an isolated layer for fast incremental builds
RUN mvn dependency:go-offline -B

# Copy project source code
COPY src ./src

# Build production JAR (skip tests to keep build fast)
RUN mvn clean package -DskipTests -B && \
    cp target/File_Sharing-*.jar target/app.jar

# ------------------------------------------------------------------------------
# Stage 2: Minimal Runtime Image
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a dedicated non-root user and group for security
RUN groupadd -r appgroup && useradd -r -g appgroup -s /bin/false appuser

# Copy executable jar from builder stage
COPY --from=builder /app/target/app.jar app.jar

# Set file permissions for the app user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Render dynamically sets the $PORT environment variable (defaults to 8080)
ENV PORT=8080
EXPOSE 8080

# JVM memory and container tuning for cloud containers (e.g. Render 512MB RAM tier)
# - UseContainerSupport: respects cgroup memory limits
# - MaxRAMPercentage=75.0: caps heap at 75% of container RAM to prevent OOM kills
# - dev/urandom: speeds up startup cryptographic initialization
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Start the Spring Boot application, dynamically binding to $PORT injected by Render
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
