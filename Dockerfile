FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon --max-workers=1 bootJar && \
    cp build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app
USER app
ENV WAS_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
