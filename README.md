# Тестирование приложения задач

Учебный REST API на Java 21 и Spring Boot. Задачи хранятся в памяти и удаляются
при перезапуске. Веб-интерфейс — Swagger UI.

| Работа | Что сделано |
| --- | --- |
| API-тесты | JUnit 5, REST Assured, OpenAPI; 95 тестов |
| [Чек-лист и баг-репорты](docs/web-testing/README.md) | 102 проверки, найдено 3 дефекта |
| [UI-тесты](docs/ui-testing/README.md) | Playwright, JUnit 5, Page Object; 5 тестов |
| [ДЗ №5](#дз-5-junit) | Пагинация в Swagger UI и аннотации JUnit 5 |
| [ДЗ №6](docs/test-patterns/README.md) | Паттерны автотестов в сценариях создания и замены задачи |

## Запуск

Нужен JDK 21 или 25. Maven Wrapper скачает Maven и зависимости:

```bash
./mvnw verify
```

На Windows — `mvnw.cmd verify`. Тесты сами запускают сервер на свободном порту.

Для UI-тестов сначала установить Chromium:

```bash
PLAYWRIGHT_BROWSERS_PATH="$PWD/.tools/ms-playwright" \
  ./mvnw exec:java -Dexec.args="install chromium"
./mvnw -Pui verify
```

Дальше достаточно `./mvnw -Pui verify`: команда запускает API- и UI-тесты.

Для ручной проверки:

```bash
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
Спецификация: `http://localhost:8080/openapi.json`.

## ДЗ 5: JUnit

В Swagger UI проверяю пустой список, порядок задач на страницах и ошибки в `limit` и `offset`.
Наборы данных задаю через `@ParameterizedTest`, сценарии с созданными задачами группирую через `@Nested`.
`@BeforeEach` готовит данные и контекст браузера, `@AfterEach` закрывает контекст.
У UI-тестов есть `@Timeout`, а `assertAll` показывает сразу все расхождения в ответе с ошибкой.
`@TestFactory` генерирует API-тесты по OpenAPI. Тест на [BUG-002](docs/web-testing/bugs/BUG-002.md)
помечен `@Disabled` до исправления.

Выбор UI-тестов по классу, пакету и тегу:

```bash
./mvnw -Pui verify -Dit.test=TaskListUiIT
./mvnw -Pui verify '-Dit.test=ru/itmo/api/*IT'
./mvnw -Pui test-compile failsafe:integration-test failsafe:verify -Dgroups=pagination
```

## API

| Метод | Путь | Ответ |
| --- | --- | --- |
| POST | `/tasks` | 201, задача и заголовок Location |
| GET | `/tasks?limit=20&offset=0` | 200, список задач |
| GET | `/tasks/{taskId}` | 200, задача |
| PUT | `/tasks/{taskId}` | 200, заменённая задача |
| DELETE | `/tasks/{taskId}` | 204, пустое тело |

У задачи три поля: обязательное `title` (1–100 символов, не только пробелы),
`priority` (целое от 1 до 5, по умолчанию 3) и `completed` (boolean, по умолчанию
false). Неверные типы, null и лишние поля отклоняются с 422. Число `4.0`
принимается как целое по правилам JSON Schema.

PUT заменяет задачу целиком: пропущенные поля получают значения по умолчанию.

## API-тесты

Написал 95 тестов на JUnit 5 и REST Assured. Проверил CRUD, пагинацию, границы
полей, неверные типы и параметры, повреждённый JSON, UTF-8 и ответы 404/405.
Перед каждым тестом хранилище очищается. После невалидных запросов проверяю,
что данные не изменились.

Springdoc формирует OpenAPI из кода; копия лежит в `openapi.json`.
`OpenApiSnapshotTest` сравнивает её с ответом приложения, а `OpenApiGeneratedTest`
создаёт тесты по операциям и ограничениям схемы. REST Assured проверяет запросы
и ответы по контракту; у заведомо невалидных запросов проверяется только ответ.

Обновить спецификацию после изменения API:

```bash
./mvnw -Dtest=OpenApiSnapshotTest -DupdateOpenApi=true test
```

## Результат и CI

На JDK 25 прошли все 95 API-тестов. Покрытие строк кода — 95,1% при пороге 85%.
HTML-отчёт: `target/site/jacoco/index.html`.

После ДЗ №5 на JDK 25 прошли 95 API- и 13 UI-тестов. Один UI-тест пропущен из-за
BUG-002. GitHub Actions запускает `./mvnw -Pui verify` на JDK 21 и 25.
В артефактах — отчёты, скриншоты и трассировки.
