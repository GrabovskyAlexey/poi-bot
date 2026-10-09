#!/usr/bin/env bash
# Разворачивает контейнер бота на сервере (по образцу scripts/deploy-backend.sh в royalguardians).
# Запускается из GitHub Actions через ssh-action и вручную.
#
# Использование:
#   bash ~/poibot/scripts/deploy-poibot.sh            # образ с тегом latest (по умолчанию)
#   bash ~/poibot/scripts/deploy-poibot.sh v0.1.3     # конкретная версия, например откат
# Переменные окружения (необязательно): IMAGE_REPO, IMAGE (полное имя образа, переопределяет репозиторий и тег),
# CONTAINER_NAME, ENV_FILE, LOG_DIR, START_TIMEOUT.
set -euo pipefail

TAG="${1:-latest}"
if ! [[ "$TAG" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "Недопустимый тег образа: $TAG" >&2
  exit 1
fi

IMAGE_REPO="${IMAGE_REPO:-ghcr.io/grabovskyalexey/poi-bot}"
IMAGE="${IMAGE:-${IMAGE_REPO}:${TAG}}"
CONTAINER_NAME="${CONTAINER_NAME:-poibot}"
ENV_FILE="${ENV_FILE:-$HOME/poibot/.env}"
LOG_DIR="${LOG_DIR:-$HOME/poibot/logs}"
START_TIMEOUT="${START_TIMEOUT:-90}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Нет файла окружения $ENV_FILE (см. docs/DEPLOY.md)" >&2
  exit 1
fi

# Логи пишутся на хост от имени пользователя деплоя, поэтому читаются обычным tail без sudo
mkdir -p "$LOG_DIR"

echo "Pulling image: ${IMAGE}"
docker pull "${IMAGE}"

if docker ps -a --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
  echo "Stopping existing container ${CONTAINER_NAME}"
  docker stop -t 30 "${CONTAINER_NAME}" || true
  echo "Removing existing container ${CONTAINER_NAME}"
  docker rm "${CONTAINER_NAME}" || true
fi

echo "Starting container ${CONTAINER_NAME}"
docker run -d \
       --network host \
       --name "$CONTAINER_NAME" \
       --env-file "$ENV_FILE" \
       --restart unless-stopped \
       --user "$(id -u):$(id -g)" \
       --stop-timeout 30 \
       --log-opt max-size=10m \
       --log-opt max-file=3 \
       -e SPRING_PROFILES_ACTIVE=prod \
       -v "$LOG_DIR:/opt/app/logs" \
       "$IMAGE"

# Деплой считается успешным, только когда бот зарегистрирован в Telegram (или контейнер упал — тогда ошибка в CI)
echo "Waiting for the bot to start (up to ${START_TIMEOUT}s)"
for ((i = 0; i < START_TIMEOUT; i += 3)); do
  if [ -z "$(docker ps --filter "name=^${CONTAINER_NAME}\$" --filter status=running -q)" ]; then
    echo "Container ${CONTAINER_NAME} stopped unexpectedly. Last logs:" >&2
    docker logs --tail 80 "${CONTAINER_NAME}" >&2 || true
    exit 1
  fi
  if docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "Registered bot running state is: true"; then
    echo "Bot started."
    docker image prune -f > /dev/null
    echo "Deployment finished: ${IMAGE}"
    exit 0
  fi
  sleep 3
done

echo "Bot did not report a successful start in ${START_TIMEOUT}s. Last logs:" >&2
docker logs --tail 80 "${CONTAINER_NAME}" >&2 || true
exit 1
