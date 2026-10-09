FROM eclipse-temurin:21
WORKDIR /opt/app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]