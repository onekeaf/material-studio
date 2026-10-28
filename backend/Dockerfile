FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/material-studio-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.awt.headless=true","-jar","app.jar"]
