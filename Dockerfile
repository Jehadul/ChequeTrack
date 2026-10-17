# Multi-stage build: compile with Maven, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home chequetrack && mkdir -p /data/attachments && chown chequetrack /data/attachments
COPY --from=build /src/target/chequetrack-*.jar app.jar
USER chequetrack
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Dhaka
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
