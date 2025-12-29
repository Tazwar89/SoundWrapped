# Use Eclipse Temurin 17 JRE as the base image (lightweight runtime)
FROM eclipse-temurin:17-jre

# Set a working directory inside the container
WORKDIR /app

# Copy the built JAR file
COPY target/soundwrapped-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your app runs on
EXPOSE 8081

# Run the JAR file
# Note: Environment variables should be provided at runtime via docker-compose or --env-file
ENTRYPOINT ["java","-jar","app.jar"]