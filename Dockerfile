# =============================================================================
# AssociaGo — backend cloud (Spring Boot) — immagine Docker per hosting remoto
#
# Build multi-stage: compila il jar eseguibile e lo esegue su una JRE snella.
# Il profilo attivo è "cloud": DataSource MySQL da variabili d'ambiente.
#
# Variabili d'ambiente richieste a runtime. Le prime cinque sono obbligatorie:
# senza, il profilo cloud rifiuta di avviarsi, ed è voluto — un backend
# pubblico che parte con default permissivi è una fuga di dati.
#
#   SPRING_DATASOURCE_URL       jdbc:mysql://<host>:<porta>/<db>?sslMode=REQUIRED
#   SPRING_DATASOURCE_USERNAME
#   SPRING_DATASOURCE_PASSWORD
#   CLERK_ISSUER_URI            https://<istanza>.clerk.accounts.dev
#   CORS_ALLOWED_ORIGINS        origini esatte del gestionale, mai "*"
#
#   CLERK_AUDIENCE              (opzionale) audience attesa nel token
#   PORT                        (opzionale) porta HTTP, default 8080
#
# AUTH_ENABLED non esiste più nel profilo cloud: sul web l'unica modalità
# ammessa è quella autenticata.
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

# Il jar viene aperto nei suoi strati naturali. Un jar Spring Boot copiato
# intero diventa un unico layer da decine di megabyte, e cambia per intero a
# ogni build anche quando è cambiata una riga: significa pagare l'intero peso in
# archivio a ogni deploy, e superare la quota gratuita di Artifact Registry dopo
# pochi rilasci. Estratto, le dipendenze — che cambiano di rado — restano un
# layer identico fra un build e l'altro, quindi conservato una volta sola.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Utente non-root
RUN useradd -r -u 1001 -m associago

# Dal meno mutevole al più mutevole: è l'ordine che rende utile la cache.
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

USER associago

ENV SPRING_PROFILES_ACTIVE=cloud
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Molti hosting free iniettano $PORT; il profilo cloud lo legge (server.port=${PORT:8080}).
# JarLauncher al posto di `-jar`: il jar non esiste più come file unico.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
