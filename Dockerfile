# Multi-stage build for optimized Docker image
# Stage 1: Build
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY settings.gradle .
COPY build.gradle .

# Fix Windows line endings and grant execution permission
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download dependencies for better layer caching
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# Install curl for health checks and create non-root user
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app

# Copy the jar file from builder stage
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Create log directory and set ownership
RUN mkdir -p /app/logs && chown -R app:app /app

# Switch to non-root user
USER app:app

# Expose ports (server, management)
EXPOSE 8080 8081

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
