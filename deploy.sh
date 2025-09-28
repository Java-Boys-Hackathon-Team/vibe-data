#!/usr/bin/env bash

# Немедленно завершать выполнение при ошибках и использовать неинициализированные переменные как ошибки
set -euo pipefail
# Логировать выполняемые команды (удобно для CI)
set -x

# =============================
# Проверка аргументов
# =============================
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <branch_name>"
  exit 1
fi
BRANCH_NAME="$1"

# =============================
# Выбор docker compose (v2 или v1)
# =============================
compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    echo "Docker Compose is not installed. Install Docker Compose v2 (docker compose) or v1 (docker-compose)." >&2
    exit 1
  fi
}

# Текущее имя проекта docker compose (по умолчанию = имя папки)
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$(pwd)")}"

# =============================
# 1) Получить обновления из git и переключиться на ветку
# =============================
git fetch --all --prune
# Переключаемся на нужную ветку (создавать локально, если ее нет)
if git rev-parse --verify --quiet "$BRANCH_NAME" >/dev/null; then
  git checkout "$BRANCH_NAME"
else
  git checkout -b "$BRANCH_NAME" "origin/$BRANCH_NAME" || git checkout "$BRANCH_NAME"
fi
# Подтянуть последние изменения
git pull --ff-only origin "$BRANCH_NAME"

# =============================
# 2) Остановить и удалить контейнеры стека (volume-ы не трогаем), удалить образы стека
# =============================
# Удалит контейнеры/сети/кэшные артефакты и ЛОКАЛЬНО собранные образы (оставит volumes)
compose down --remove-orphans --rmi local || true

# На всякий случай подчистим висящие контейнеры/образы с меткой проекта
# (не затрагиваем volumes)
CONTAINERS_TO_REMOVE=$(docker ps -aq --filter "label=com.docker.compose.project=${PROJECT_NAME}" || true)
if [ -n "${CONTAINERS_TO_REMOVE}" ]; then
  docker rm -f ${CONTAINERS_TO_REMOVE} || true
fi
IMAGES_TO_REMOVE=$(docker images -q --filter "label=com.docker.compose.project=${PROJECT_NAME}" || true)
if [ -n "${IMAGES_TO_REMOVE}" ]; then
  docker rmi -f ${IMAGES_TO_REMOVE} || true
fi

# =============================
# 3) Сборка проекта (jar для образа)
# =============================
./gradlew clean bootJar -x test

# =============================
# 4) Запуск всего стека (пересборка образов при необходимости)
# =============================
compose up -d --build

echo "Deployment completed successfully on branch ${BRANCH_NAME}"