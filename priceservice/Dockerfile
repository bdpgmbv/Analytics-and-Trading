FROM gradle:8.6-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

FROM gcr.io/distroless/java21-debian12
COPY --from=builder /app/build/libs/priceservice-1.0.0-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]