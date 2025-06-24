# Multi-stage Docker build for L09ClientMCP
# Production-ready container with security hardening

# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage - use distroless for security
FROM gcr.io/distroless/java21-debian12:nonroot

# Labels for container metadata
LABEL maintainer="Coherent Solutions <support@coherentsolutions.com>"
LABEL org.opencontainers.image.title="L09ClientMCP"
LABEL org.opencontainers.image.description="Spring AI MCP Client - Production Ready"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Coherent Solutions"

# Create app directory and copy jar
WORKDIR /app
COPY --from=builder /app/target/L09ClientMCP-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD ["java", "-cp", "app.jar", "org.springframework.boot.loader.launch.JarLauncher", \
         "--spring.main.web-application-type=none", \
         "--spring.main.application-class=HealthCheckApplication"]

# JVM configuration for production
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# Run as non-root user (distroless default)
USER nonroot

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]