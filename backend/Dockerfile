FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S wellness && adduser -S wellness -G wellness
COPY --from=build /workspace/target/wellness-backend-*.jar app.jar
USER wellness
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

