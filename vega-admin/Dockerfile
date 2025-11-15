# --- STAGE 1: Build ---
# Use a specific, consistent JDK base image (Ubuntu 22.04 LTS 'jammy')
# This ensures both builds run in the exact same environment.
FROM eclipse-temurin:21-jdk-jammy AS builder

# Set the working directory
WORKDIR /app

# Argument for Maven cache, useful for CI/CD optimization
ARG MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository"
ENV MAVEN_OPTS=${MAVEN_OPTS}

# 1. Copy build descriptors first to leverage Docker layer caching
COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .

# 2. Download dependencies using BuildKit's cache mount for persistent caching
# This is more efficient than a simple 'RUN ./mvnw dependency:go-offline'
# as the cache is persisted between builds, not just as a Docker layer.
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline

# 3. Copy application source code
COPY src ./src

# 4. Build and package the application
# Skip tests (these should be run in a separate CI stage)
# Use the cache mount again for the build process
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests

# --- STAGE 2: Runtime ---
# Use a minimal, secure JRE image based on the same LTS
FROM eclipse-temurin:21-jre-alpine

# Set the working directory
WORKDIR /app

# Create a non-root user and group with specific, stable IDs (1001).
# log directory is created for application logs
RUN addgroup --gid 1001 appgroup && \
    adduser --uid 1001 -G appgroup -D -h /app appuser && \
    mkdir /app/logs && \
    chown -R appuser:appgroup /app /app/logs

# Copy the built JAR from the 'builder' stage.
COPY --from=builder /app/target/vega-*.jar app.jar

# Ensure the final 'app.jar' is owned by the non-root user
RUN chown appuser:appgroup app.jar

# Switch to the non-root user
USER appuser

# Expose the application port
EXPOSE 9091

# Set the entrypoint to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
