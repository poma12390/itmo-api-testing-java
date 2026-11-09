# Автоматизация UI-тестирования

Написал пять тестов для Swagger UI приложения задач из второй лабораторной.
Использовал Playwright for Java 1.63.0, JUnit 5 и Page Object.

## Сценарии

| Тест | Действия | Ожидаемый результат |
| --- | --- | --- |
| UI-01 | Создать задачу с кириллицей и кавычками, перезагрузить страницу, прочитать задачу и список | 201 при создании, 200 при чтении; все поля сохранились, в списке одна задача |
| UI-02 | Создать задачу, передав только название из 100 символов | Название принято, priority=3, completed=false |
| UI-03 | Создать название из одного символа с приоритетом 1; заменить поля, указав приоритет 5 и completed=true | ID остался прежним; новые значения доступны после перезагрузки, дубликата нет |
| UI-04 | Прочитать задачу, удалить её, повторить чтение и удаление | Удаление — 204 без тела; повторные обращения — 404; список пуст |
| UI-05 | Отправить POST с пробельным названием и PUT с приоритетом 6 | 422 с полем ошибки; контрольная задача не изменилась |

Проверил основные операции, допустимые и недопустимые значения, границы длины
1/100 и приоритета 1/5/6. Код сценариев — в [TaskUiIT.java](../../src/test/java/ru/itmo/api/TaskUiIT.java).

## Как устроены тесты

В Swagger UI пользователь раскрывает операцию, нажимает **Try it out**, вводит
параметры и нажимает **Execute**. Тесты повторяют эти действия в Chromium.
Ответы читаются из **Server response** — справочные примеры схем не используются.

[SwaggerUiPage](../../src/test/java/ru/itmo/ui/pages/SwaggerUiPage.java) содержит
действия `createTask`, `readTask`, `replaceTask`, `deleteTask` и `listTasks`.
Общий компонент [OperationPanel](../../src/test/java/ru/itmo/ui/pages/OperationPanel.java)
работает с формой операции. `Page` и локаторы закрыты внутри этих классов;
в тестах остаются вызовы действий и проверки JUnit. Тела ответов сравниваются
как JSON, поэтому форматирование и порядок полей не влияют на результат.

Перед повторным запросом компонент очищает старый ответ через **Clear** и ждёт
новый. Иначе GET после удаления мог бы прочитать предыдущий ответ 200.
Для ожиданий использовал локаторы и `assertThat` Playwright, без `Thread.sleep`.

[UiTestSupport](../../src/test/java/ru/itmo/api/UiTestSupport.java) запускает
приложение на случайном порту и Chromium на весь класс. Перед каждым тестом
очищается хранилище и создаётся отдельный `BrowserContext`; после теста он
закрывается. Данные создаются через интерфейс, ID берётся из ответа.

## Запуск

Нужен JDK 21 или 25. Из корня проекта установить Chromium и запустить тесты:

```bash
PLAYWRIGHT_BROWSERS_PATH="$PWD/.tools/ms-playwright" \
  ./mvnw exec:java -Dexec.args="install chromium"
./mvnw -Pui verify
```

При следующих запусках нужна только вторая команда. Профиль `ui` добавляет
браузерные тесты через Maven Failsafe; обычный `./mvnw verify` запускает 95
API-тестов. Для UI нужен этап `verify`, а не `test`.

На Linux при нехватке библиотек заменить аргумент установки на
`-Dexec.args="install --with-deps chromium"`. На Windows из PowerShell:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = "$PWD/.tools/ms-playwright"
.\mvnw.cmd exec:java '-Dexec.args=install chromium'
.\mvnw.cmd -Pui verify
```

## Результаты

Прогон от 18.10.2026: macOS 26.7.1, JDK 25.0.2, Chromium 153.0.8010.12,
без окна браузера, размер страницы 1280×900.

| Набор | Прошли | Ошибки | Пропущены |
| --- | ---: | ---: | ---: |
| UI-тесты | 5 | 0 | 0 |
| API-тесты | 95 | 0 | 0 |
| **Всего** | **100** | **0** | **0** |

Чистая сборка заняла 27,6 с. Покрытие строк кода приложения — 95,06% (77 из 81).
[Журнал, JUnit-отчёт и скриншоты](evidence/README.md) сохранены в репозитории.
В GitHub Actions настроен тот же запуск на JDK 21 и 25.

Проверял Chromium на macOS. Найденные ранее [ошибки обработки JSON и Unicode](../web-testing/bugs/README.md)
остаются открытыми; в UI-05 использованы обычные пробелы, табуляция и перевод строки.

## Отладка

Результаты UI-тестов лежат в `target/failsafe-reports/`. Для каждого теста в
`target/ui-artifacts/<имя метода>/` сохраняются `page.png`, `page.html` и
`trace.zip`. Они также загружаются в артефакты CI при падении тестов.

Открыть окно браузера или запустить один сценарий:

```bash
./mvnw -Pui -Dui.headed=true verify
./mvnw -Pui '-Dit.test=TaskUiIT#deletesTaskAndShowsNotFound' verify
```

Посмотреть действия и сеть в трассировке:

```bash
PLAYWRIGHT_BROWSERS_PATH="$PWD/.tools/ms-playwright" \
  ./mvnw exec:java \
  -Dexec.args="show-trace target/ui-artifacts/deletesTaskAndShowsNotFound/trace.zip"
```

Документация Playwright: [JUnit](https://playwright.dev/java/docs/test-runners),
[Page Object](https://playwright.dev/java/docs/pom),
[установка браузеров](https://playwright.dev/java/docs/browsers),
[трассировки](https://playwright.dev/java/docs/trace-viewer).
