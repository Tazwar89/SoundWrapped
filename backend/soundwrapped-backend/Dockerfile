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
COPY --from=builder /app/target/*.jar app.jar

# Expose the port Render expects (default 10000)
EXPOSE 10000

# JVM tuning + ensure binding to 0.0.0.0 and PORT env
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx400m -XX:TieredStopAtLevel=1 -Dserver.address=0.0.0.0 -Dserver.port=${PORT}"

# Run the JAR file
ENTRYPOINT ["java","-jar","app.jar"]