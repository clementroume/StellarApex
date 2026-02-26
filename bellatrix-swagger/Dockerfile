# ==============================================================================
# STAGE 1: Builder (Maven Build)
# ==============================================================================
# Use a standard JDK image to compile the application.
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and configuration files first.
# This leverages Docker layer caching: if pom.xml doesn't change,
# dependencies won't be re-downloaded in the next step.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies offline to speed up subsequent builds.
RUN chmod +x mvnw && ./mvnw dependency:go-offline

# Copy the actual application source code.
COPY src src

# Build the executable JAR, skipping tests for faster build times.
RUN ./mvnw clean package -DskipTests

# ==============================================================================
# STAGE 2: Optimizer (Layer Extraction)
# ==============================================================================
# Use a JRE image to extract the Spring Boot layers.
# This allows us to copy dependencies and application code separately in the final stage,
# optimizing storage and caching.
FROM eclipse-temurin:25-jre AS optimizer

WORKDIR /app

# Retrieve the JAR built in the previous stage.
COPY --from=builder /app/target/*.jar app.jar

# Extract the layers (dependencies, spring-boot-loader, etc.) into the 'extracted' directory.
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

# ==============================================================================
# STAGE 3: Runtime (Distroless Base)
# ==============================================================================
# Use the Google Distroless image for Java 25.
# This image is minimal (no shell, no package manager) and runs as non-root for enhanced security.
FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app

# Copy the extracted layers from the optimizer stage.
# The order matters: 'dependencies' change least often, so they are copied first.
# 'application' changes most often, so it is copied last to invalidate fewer cache layers.
COPY --from=optimizer /app/extracted/dependencies/ ./
COPY --from=optimizer /app/extracted/spring-boot-loader/ ./
COPY --from=optimizer /app/extracted/snapshot-dependencies/ ./
COPY --from=optimizer /app/extracted/application/ ./

# Expose application ports
EXPOSE 8888

# Start the application using the Spring Boot JarLauncher.
# It automatically reconstructs the classpath from the layers.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]