# --- STAGE 1: Build Angular ---
# Use a lightweight Node.js 'alpine' image for the build environment
FROM node:22-alpine AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy package manifests (package.json and package-lock.json)
COPY package*.json ./

# Install dependencies using 'npm ci' for a clean, deterministic, and fast install.
# --no-audit and --no-fund are added to speed up CI/CD builds by skipping optional checks.
RUN npm ci --no-audit --no-fund

# Copy the rest of the application source code
COPY . .

# Build the Angular application for production.
# The output will be in '/app/dist/antares-app/browser'.
RUN npm run build -- --configuration production

# --- STAGE 2: Runtime (nginx) ---
# Use a minimal, stable nginx 'alpine' image for serving the static files
FROM nginx:stable-alpine

# Remove the default nginx configuration file
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy our custom nginx configuration from the build context
# This file must exist in the same directory (antares-app)
COPY nginx.conf /etc/nginx/nginx.conf

# Copy the built application artifacts from the 'builder' stage
# into the directory nginx serves files from.
COPY --from=builder /app/dist/sirius-app/browser /usr/share/nginx/html

# Set correct permissions for the static files.
# The base nginx image runs as a non-root 'nginx' user by default.
# This command ensures that user has permission to read the files.
RUN chmod -R 755 /usr/share/nginx/html && chown -R nginx:nginx /usr/share/nginx/html

# Expose standard HTTP (80) and HTTPS (443) ports
# The docker-compose.yml will be responsible for mapping these to host ports.
EXPOSE 80 443

# Start nginx in the foreground (the standard way to run nginx in Docker)
CMD ["nginx", "-g", "daemon off;"]
