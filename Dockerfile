FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew :nexus-app:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache wget && \
    addgroup -S nexus && adduser -S nexus -G nexus
USER nexus
COPY --from=build /app/nexus-app/build/libs/nexus-app*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
