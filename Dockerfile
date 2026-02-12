FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn

RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates ca-certificates-java \
    && update-ca-certificates -f \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"

ENV SERVER_PORT=8787
WORKDIR /app

COPY .env /app/.env
COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8787
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
