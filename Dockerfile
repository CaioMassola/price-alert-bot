FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY checkstyle.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 bot
COPY --from=build /build/target/price-alert-bot-0.1.0.jar app.jar
USER bot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
