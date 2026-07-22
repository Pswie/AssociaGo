# =============================================================================
# AssociaGo — backend cloud (Spring Boot) — immagine Docker per hosting remoto
#
# Build multi-stage: compila il jar eseguibile e lo esegue su una JRE snella.
# Il profilo attivo è "cloud": DataSource MySQL da variabili d'ambiente.
#
# Variabili d'ambiente richieste a runtime:
#   SPRING_DATASOURCE_URL       jdbc:mysql://<host>:3306/associago?useSSL=true&requireSSL=true
#   SPRING_DATASOURCE_USERNAME
#   SPRING_DATASOURCE_PASSWORD
#   PORT                        (opzionale) porta HTTP; molti hosting la iniettano, default 8080
#   CORS_ALLOWED_ORIGINS        (opzionale) origini consentite; default "*"
#   AUTH_ENABLED                (opzionale) "true" per attivare il login
# =============================================================================

# ---- Stage 1: build ---------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Wrapper e script di build prima del sorgente, per sfruttare la cache dei layer
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew

# Sorgente e config (checkstyle)
COPY config ./config
COPY src ./src

# Solo il jar eseguibile (niente test/checkstyle in fase di packaging)
RUN ./gradlew --no-daemon clean bootJar \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)" /app/app.jar

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Utente non-root
RUN useradd -r -u 1001 -m associago
USER associago

COPY --from=build /app/app.jar app.jar

ENV SPRING_PROFILES_ACTIVE=cloud
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Molti hosting free iniettano $PORT; il profilo cloud lo legge (server.port=${PORT:8080})
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
