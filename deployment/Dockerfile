# Build stage: compile the Spring Boot jar with the Gradle wrapper
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :app-service:bootJar --no-daemon -x test

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appuser && adduser -S appuser -G appuser
WORKDIR /app
COPY --from=build /workspace/applications/app-service/build/libs/franchise_inventory_api.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"
USER appuser
EXPOSE 8080
ENTRYPOINT [ "/bin/sh", "-c", "java $JAVA_OPTS -jar /app/app.jar" ]
