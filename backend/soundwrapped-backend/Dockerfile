# Use an official OpenJDK 17 runtime as the base image
FROM openjdk:17-jdk-slim

# Set a working directory inside the container
WORKDIR /app

# Copy the built JAR and .env file
COPY target/soundwrapped-backend-0.0.1-SNAPSHOT.jar app.jar
COPY .env .

# Expose the port your app runs on
EXPOSE 8081

# Use the env file
ENV $(cat .env | xargs)

# Run the JAR file
ENTRYPOINT ["java","-jar","app.jar"]