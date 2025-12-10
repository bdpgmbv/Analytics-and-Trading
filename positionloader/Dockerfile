FROM gradle:8.6-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

FROM gcr.io/distroless/java21-debian12
# FIXED: Changed from positionloader-1.0.0-SNAPSHOT.jar to positionloader.jar
# The bootJar task in build.gradle.kts creates positionloader.jar
COPY --from=builder /app/build/libs/positionloader.jar /app/app.jar

# FIXED: Made profile configurable via build arg with default
ARG SPRING_PROFILE=prod
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]