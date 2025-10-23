# --- STAGE 1: Build ---
# Utilisation d'une image Node.js alpine pour un build léger
FROM node:22-alpine AS builder

WORKDIR /app

# Copier les fichiers de dépendances et installer
COPY package*.json ./
RUN npm install

# Copier le reste du code source
COPY . .

# Lancer le build de production de l'application Angular
RUN npm run build -- --configuration production

# --- STAGE 2: Serve ---
# Utilisation d'une image Nginx alpine stable et légère
FROM nginx:stable-alpine

# Supprimer la configuration Nginx par défaut
RUN rm /etc/nginx/conf.d/default.conf

# Copier notre configuration Nginx personnalisée
COPY nginx.conf /etc/nginx/conf.d

# Copier l'application buildée depuis l'étage 'builder'
COPY --from=builder /app/dist/antares-app/browser /usr/share/nginx/html

# Exposer le port 80
EXPOSE 80

# La commande par défaut de l'image nginx est 'nginx -g "daemon off;"'
# ce qui est parfait pour nous.
