# ==============================================================================
# STAGE 1: Builder (Maven Build)
# ==============================================================================
# We use a standard Debian-based JDK image (instead of Alpine) to ensure
# binary compatibility (glibc) with the final Distroless image.
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and configuration to leverage Docker layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies offline. This layer is cached if pom.xml doesn't change.
RUN chmod +x mvnw && ./mvnw dependency:go-offline

# Copy source code and build the application (skipping tests for speed)
COPY src src
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# STAGE 2: Optimizer (Layer Extraction)
# ==============================================================================
# Extract Spring Boot layers to optimize Docker caching for the final image.
FROM eclipse-temurin:25-jre AS optimizer

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# Extract the layers (dependencies, spring-boot-loader, snapshot-dependencies, application)
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# ==============================================================================
# STAGE 3: JLinker (Custom JRE Creation)
# ==============================================================================
# Create a custom, minimal JRE containing only the necessary modules.
# This significantly reduces the final image size compared to a full JDK.
FROM eclipse-temurin:25-jdk AS jlinker

# Build a custom JRE compatible with Debian (glibc).
# We include standard modules required for a Spring Boot Web application.
RUN "$JAVA_HOME/bin/jlink" \
    --add-modules java.base,java.logging,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.sql,java.net.http,jdk.unsupported,jdk.crypto.ec,java.rmi,java.xml,java.compiler \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

# ==============================================================================
# STAGE 4: Runtime (Distroless Base)
# ==============================================================================
# Use the "base" Distroless image (contains only glibc, ssl, and standard libs).
# It does not contain Java, a shell, or a package manager, reducing the attack surface.
FROM gcr.io/distroless/base-debian12:nonroot

WORKDIR /app

# 1. Install the custom JRE generated in the previous stage
COPY --from=jlinker /javaruntime /opt/java/openjdk

# 2. Configure environment variables to use the custom JRE
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# 3. Copy the application layers from the optimizer stage.
# The order matters for caching: dependencies change least often.
COPY --from=optimizer /app/extracted/dependencies/ ./
COPY --from=optimizer /app/extracted/spring-boot-loader/ ./
COPY --from=optimizer /app/extracted/snapshot-dependencies/ ./
COPY --from=optimizer /app/extracted/application/ ./

# Expose the application ports
EXPOSE 8081 9092

# Start the application using the Spring Boot JarLauncher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]