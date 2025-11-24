# ==========================================
# STAGE 1: Builder (Maven Build)
# ==========================================
# Use the full JDK to build the application
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and configuration to cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies. This layer is cached if pom.xml doesn't change.
RUN chmod +x mvnw && ./mvnw dependency:go-offline

# Copy source code and build the application (skipping tests for speed)
COPY src src
RUN ./mvnw clean package -DskipTests

# ==========================================
# STAGE 2: Optimizer (Layer Extraction)
# ==========================================
# Use a lightweight JRE to extract the Spring Boot layers
FROM eclipse-temurin:25-jre-alpine AS optimizer
WORKDIR /app

# Retrieve the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Extract the layers (dependencies, internal code, etc.) for optimized Docker caching
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# ==========================================
# STAGE 3: JLinker (Custom JRE Creation)
# ==========================================
# Create a minimal custom JRE to reduce the final image size
FROM eclipse-temurin:25-jdk-alpine AS jlinker

# Build a custom JRE containing only the modules required by Spring Boot/JPA/Web.
# This significantly reduces the runtime footprint compared to a full JRE.
RUN "$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.logging,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.sql,java.net.http,jdk.unsupported,jdk.crypto.ec,java.rmi,java.xml \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

# ==========================================
# STAGE 4: Runtime (Final Alpine Image)
# ==========================================
# Use a specific Alpine version for stability and security
FROM alpine:3.22.2

WORKDIR /app

# Install system dependencies (gcompat is required for JVM on Alpine)
# and create a non-root user for security best practices.
RUN apk add --no-cache gcompat && \
    addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D -h /app appuser && \
    mkdir /app/logs && \
    chown -R appuser:appgroup /app

# Import the custom JRE from the jlinker stage
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jlinker /javaruntime "$JAVA_HOME"

# Copy the application layers from the optimizer stage.
# The order matters: dependencies change least often, so they are copied first.
COPY --from=optimizer /app/extracted/dependencies/ ./
COPY --from=optimizer /app/extracted/spring-boot-loader/ ./
COPY --from=optimizer /app/extracted/snapshot-dependencies/ ./
COPY --from=optimizer /app/extracted/application/ ./

# Switch to the non-root user
USER appuser

# Expose the application ports (8080/9090 for Antares, 9091 for Vega)
EXPOSE 9091

# Start the application using the Spring Boot JarLauncher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]