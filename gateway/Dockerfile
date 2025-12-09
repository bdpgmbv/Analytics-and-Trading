# Use a lightweight Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine

# Add a volume pointing to /tmp (optional, for Tomcat)
VOLUME /tmp

# The application's jar file
ARG JAR_FILE=build/libs/*.jar

# Add the application's jar to the container
COPY ${JAR_FILE} app.jar

# Run the jar file
ENTRYPOINT ["java","-jar","/app.jar"]