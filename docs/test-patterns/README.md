# ДЗ №6. Паттерны проектирования автотестов

Переписал два сценария в [TaskUiIT](../../src/test/java/ru/itmo/api/TaskUiIT.java).
В UI-01 создаю задачу с кириллицей и кавычками, перезагружаю Swagger UI и
сверяю GET и список. В UI-03 проверяю два варианта PUT.

| Вариант PUT | Проверка после перезагрузки |
| --- | --- |
| Все поля заданы | ID прежний, название изменилось, приоритет стал `5`, статус — `true` |
| Передано только название | ID прежний, приоритет сбросился с `1` до `3`, статус — с `true` до `false` |

В обоих вариантах сверяю тело ответа PUT, повторный GET и список из одной задачи.

## Паттерны

| Паттерн | Как использован |
| --- | --- |
| Page Object | `SwaggerUiPage` даёт тестам операции над задачами и прячет локаторы Swagger UI. [Класс](../../src/test/java/ru/itmo/ui/pages/SwaggerUiPage.java) |
| Page Element / Wrapper | `OperationPanel` управляет формой, `ServerResponsePanel` ждёт и читает результат, включая пустое тело при `204`. При повторном запуске формы старый ответ должен исчезнуть до клика по Execute. [Форма](../../src/test/java/ru/itmo/ui/pages/OperationPanel.java) · [Ответ](../../src/test/java/ru/itmo/ui/pages/ServerResponsePanel.java) |
| Loadable Component | `open()` и `reload()` возвращают готовую страницу. `awaitLoaded()` ждёт заголовок Tasks API и видимые блоки создания и списка. |
| Chain of Invocations | `ui.reload().readTask(id)` читает задачу после готовности страницы. В форме PUT `taskId(id).body(...).execute()` задаёт параметры перед отправкой. |
| Value Object | `TaskInput` хранит поля запроса и ожидаемые значения. Если `priority` или `completed` отсутствуют, Jackson пропускает их при сериализации. `UiResponse` хранит код и тело прочитанного ответа. [TaskInput](../../src/test/java/ru/itmo/ui/TaskInput.java) · [UiResponse](../../src/test/java/ru/itmo/ui/UiResponse.java) |
| Data Provider | `@MethodSource("replacements")` передаёт исходную задачу, запрос PUT и ожидаемое состояние. Оба набора проходят через один сценарий с отдельным контекстом браузера на каждый запуск. |

## Запуск

Для UI-тестов нужен Chromium. [Установка и полный запуск](../../README.md#запуск)

```bash
./mvnw -Pui verify -Dit.test=TaskUiIT
./mvnw -Pui clean verify
```

## Результат

На JDK 25.0.2 и Chromium 153.0.8010.12 прошли 95 API- и 14 UI-тестов.
Один UI-тест пропущен из-за [BUG-002](../web-testing/bugs/BUG-002.md).
Покрытие строк — 95,06% при пороге 85%. [Журнал Maven](evidence/verify.txt)

Из трассировок обоих вариантов PUT сохранил тела запросов и ответы.
Скриншоты показывают GET и список после перезагрузки. [HTTP-обмены](evidence/put-exchanges.json) · [Все поля](evidence/explicit-fields.png) · [Только название](evidence/omitted-fields.png)
