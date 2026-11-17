# ДЗ №7. XPath/CSS-селекторы и JUnit

Переписал локаторы Swagger UI в Page Object из ДЗ №6. Для элементов с
устойчивыми атрибутами использовал CSS, для кнопок с известным текстом — XPath.

## Локаторы

| Элемент | Селектор | Область поиска |
| --- | --- | --- |
| Заголовок API | `#swagger-ui .info h1.title` | Страница |
| Операция | `#operations-task-controller-{operationId}` | Страница |
| Раскрытие операции | `button.opblock-summary-control[aria-expanded]` | Операция |
| Try it out, Cancel, Execute, Clear | `.//button[normalize-space(.)='{text}']` | Операция |
| Параметр запроса | `tr[data-param-name='{name}'] input[type='text']` | Операция |
| Тело запроса | `textarea.body-param__text` | Операция |
| Ошибки параметров | `.validation-errors li` | Операция |
| Таблица ответа сервера | `table.live-responses-table` | Операция |
| HTTP-код | `tbody td.response-col_status` | Таблица ответа сервера |
| Тело ответа | `tbody td.response-col_description .highlight-code > pre` | Таблица ответа сервера |

`operationId` и имена параметров взяты из контракта API. Случайные ID вкладок
Swagger UI и классы состояния вроде `is-open` и `invalid` в локаторах не использую.
XPath начинается с `.//`, поэтому одинаковые кнопки соседних операций не мешают
друг другу. `normalize-space(.)` убирает лишние пробелы при точном сравнении текста.

Нет абсолютных XPath, порядковых индексов, `first()` или `nth()`.
Для одиночных элементов действует строгий режим Playwright: совпадение с
несколькими элементами приводит к ошибке. Код и тело ответа читаю только из
`live-responses-table`, отдельно от примеров схем и заголовков ответа.

Локаторы находятся в [SwaggerUiPage](../../src/test/java/ru/itmo/ui/pages/SwaggerUiPage.java),
[OperationPanel](../../src/test/java/ru/itmo/ui/pages/OperationPanel.java) и
[ServerResponsePanel](../../src/test/java/ru/itmo/ui/pages/ServerResponsePanel.java).
Тесты работают через методы этих классов. Сохранил сценарии CRUD и пагинации,
параметризацию JUnit 5, подготовку данных и отдельный контекст браузера на каждый
тест. Ожидания выполняются через Playwright без фиксированных пауз.

## Запуск

После [установки Chromium](../../README.md#запуск):

```bash
./mvnw -Pui clean verify
```

## Результат

17.11.2026 на JDK 25.0.2 и Chromium 153.0.8010.12 прошли 95 API- и 14 UI-тестов,
ошибок нет. Один UI-тест пропущен из-за ранее известного
[BUG-002](../web-testing/bugs/BUG-002.md). Покрытие строк — 95,06% при пороге 85%.
[Результат запуска Maven](evidence/verify.txt)

Полные отчёты JUnit находятся в `target/surefire-reports/` и
`target/failsafe-reports/`. HTML страницы, скриншоты и трассировки каждого
UI-сценария сохраняются в `target/ui-artifacts/`.
