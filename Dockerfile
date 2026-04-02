FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/target/gateway-device-app-0.0.1-jar-with-dependencies.jar app.jar
COPY config ./config
CMD ["java", "-jar", "app.jar"]
