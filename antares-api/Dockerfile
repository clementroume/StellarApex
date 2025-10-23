# --- STAGE 1: Build ---
# Utilisation d'une image JDK complète pour la compilation avec Maven
FROM eclipse-temurin:21-jdk AS builder

# Argument pour le cache Maven, utilisé par la commande RUN --mount
ARG MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository"
ENV MAVEN_OPTS=${MAVEN_OPTS}

WORKDIR /app

# 1. Copier uniquement les fichiers de build pour profiter du cache Docker
COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .

# 2. Télécharger les dépendances Maven. Cette couche est mise en cache tant que pom.xml ne change pas.
# Utilisation du montage de type 'cache' est la méthode moderne et recommandée.
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline

# 3. Copier les sources de l'application
COPY src ./src

# 4. Compiler et packager l'application, en sautant les tests qui devraient être faits en CI/CD
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean install -DskipTests

# --- STAGE 2: Runtime ---
# Utilisation d'une image JRE minimale et sécurisée pour l'exécution
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Création d'un utilisateur et d'un groupe non-root pour des raisons de sécurité
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid 1001 -m appuser

# Copier le JAR depuis l'étage de build.
# On est plus spécifique sur le nom pour éviter les ambiguïtés.
COPY --from=builder /app/target/antares-api-*.jar app.jar

# Changer le propriétaire du fichier
RUN chown appuser:appgroup app.jar

# Passer à l'utilisateur non-root
USER appuser

# Exposer le port de l'application
EXPOSE 8080

# Commande de lancement de l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
