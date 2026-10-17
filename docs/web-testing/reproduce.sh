#!/bin/sh
# Запуск из любого каталога; используется только отдельный тестовый экземпляр.
# Скрипт создаёт задачи и показывает состояние, а не исправляет дефекты.
set -eu

bug=${1:-}
base=${2:-http://127.0.0.1:18080}
base=${base%/}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

case "$bug" in
  BUG-001) fixture=bug-001-trailing-object.txt; expected_post=400; expected_put=400 ;;
  BUG-002) fixture=bug-002-nbsp.json; expected_post=422; expected_put=422 ;;
  BUG-003) fixture=bug-003-emoji-51.json; expected_post=201; expected_put=200 ;;
  *) printf 'Использование: sh %s BUG-001|BUG-002|BUG-003 [http://127.0.0.1:18080]\n' "$0" >&2; exit 2 ;;
esac

case "$base" in
  http://127.0.0.1:*|http://localhost:*) ;;
  *) printf 'Укажите отдельный локальный экземпляр через http://127.0.0.1:PORT или http://localhost:PORT\n' >&2; exit 2 ;;
esac

printf '\n%s. Тело запроса:\n' "$bug"
cat "$script_dir/fixtures/$fixture"
printf '\nИсходный список:\n'
curl --silent --show-error --connect-timeout 5 --max-time 15 --include "$base/tasks?limit=100"

printf '\n\nPOST: ожидается HTTP %s. Фактический ответ:\n' "$expected_post"
curl --silent --show-error --connect-timeout 5 --max-time 15 --include \
  -X POST "$base/tasks" -H 'Content-Type: application/json' \
  --data-binary "@$script_dir/fixtures/$fixture"

# Создать собственную существующую задачу для варианта PUT и прочитать Location.
creation=$(curl --silent --show-error --connect-timeout 5 --max-time 15 --include \
  -X POST "$base/tasks" -H 'Content-Type: application/json' \
  --data-binary '{"title":"Исходная задача","priority":2,"completed":true}')
task_path=$(printf '%s\n' "$creation" | tr -d '\r' | awk 'tolower($1)=="location:" { print $2; exit }')
case "$task_path" in
  /tasks/[0-9]*) ;;
  *) printf '\nНе выполнено предусловие PUT: POST не вернул Location.\n%s\n' "$creation" >&2; exit 1 ;;
esac

printf '\n\nИсходная задача для PUT (%s):\n' "$task_path"
curl --silent --show-error --connect-timeout 5 --max-time 15 --include "$base$task_path"
printf '\n\nPUT: ожидается HTTP %s. Фактический ответ:\n' "$expected_put"
curl --silent --show-error --connect-timeout 5 --max-time 15 --include \
  -X PUT "$base$task_path" -H 'Content-Type: application/json' \
  --data-binary "@$script_dir/fixtures/$fixture"

printf '\n\nТа же задача после PUT:\n'
curl --silent --show-error --connect-timeout 5 --max-time 15 --include "$base$task_path"
printf '\n\nСписок после проверки:\n'
curl --silent --show-error --connect-timeout 5 --max-time 15 --include "$base/tasks?limit=100"
printf '\n\nСравните статусы и состояние с баг-репортом. Код 0 означает завершение запросов, а не отсутствие дефекта.\n'
