FROM gradle:8.11.1-jdk17 AS builder

WORKDIR /workspace

COPY . .
RUN gradle :smite-api:bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /workspace/smite-api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
