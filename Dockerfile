FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV SERVER_PORT=8787
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8787
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
