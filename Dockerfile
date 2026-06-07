# --- Stage 1: build ---
# Compila l'applicazione e produce il JAR eseguibile usando Maven + JDK 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copio prima il solo pom.xml per sfruttare la cache dei layer Docker:
# le dipendenze vengono riscaricate solo se il pom cambia.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copio il codice e costruisco il JAR (i test sono eseguiti nella pipeline CI, non qui)
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Stage 2: runtime ---
# Immagine finale leggera con la sola JRE: contiene solo il necessario per eseguire.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Eseguo come utente non privilegiato (buona pratica di sicurezza)
RUN useradd --system --no-create-home appuser
USER appuser

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
