# ---- Stage 1: Build ----
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# Set a working directory inside the container
WORKDIR /app

# Copy everything
COPY . .

# Build the jar
RUN mvn clean package -DskipTests

# ---- Stage 2: Run ----
FROM eclipse-temurin:17-jre

# Set a working directory inside the container
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/target/soundwrapped-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your app runs on
EXPOSE 8080

# Run the JAR file
# Note: Environment variables should be provided at runtime via docker-compose or --env-file
ENTRYPOINT ["java","-jar","app.jar"]