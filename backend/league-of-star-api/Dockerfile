FROM gradle:8.11.1-jdk17 AS builder

WORKDIR /workspace

COPY . .
RUN gradle :league-of-star-api:bootJar -x test -x copyOasToSwagger --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/league-of-star-api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
