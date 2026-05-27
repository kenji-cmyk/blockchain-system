FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json frontend/
RUN cd frontend && npm ci
COPY frontend frontend
COPY backend/src/main/resources backend/src/main/resources
RUN cd frontend && npm run build

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app/backend
COPY backend/pom.xml .
COPY backend/src ./src
COPY --from=frontend-build /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
