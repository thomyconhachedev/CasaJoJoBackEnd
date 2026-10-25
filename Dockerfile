# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom: Docker cachea las dependencias si no cambian
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

# En una EC2 pequeña (con MySQL en la misma máquina) se puede cambiar al
# ejecutar: -e JAVA_TOOL_OPTIONS="-Xmx384m"
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
