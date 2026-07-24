# Build stage: compila el jar ejecutable con Gradle
FROM gradle:8-jdk17 AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

# Run stage: imagen final liviana, solo el JRE + el jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/sistema-practicas-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
