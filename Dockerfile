# Образ собирается из готовой jar: сначала `./gradlew bootJar`, затем `docker build -t poibot .`
FROM eclipse-temurin:21-jre

# Связывает пакет в GHCR с репозиторием: образ наследует его видимость (публичный репозиторий — публичный образ)
# и GITHUB_TOKEN из Actions получает право публиковать новые версии
LABEL org.opencontainers.image.source="https://github.com/GrabovskyAlexey/poi-bot"

RUN useradd --system --uid 10001 --create-home poibot \
    && mkdir -p /opt/app/logs \
    && chown -R poibot:poibot /opt/app

WORKDIR /opt/app
COPY --chown=poibot:poibot build/libs/poibot.jar app.jar

USER poibot
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
