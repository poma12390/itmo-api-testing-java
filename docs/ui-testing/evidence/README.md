# Результаты UI-тестов

Прогон от 18.10.2026 на macOS 26.7.1, JDK 25.0.2 и Chromium 153.0.8010.12.
Прошли 5 UI- и 95 API-тестов.

```bash
MAVEN_USER_HOME="$PWD/.m2" ./mvnw -o -B --no-transfer-progress -Pui clean verify
```

| Файл | Содержание |
| --- | --- |
| [verify.txt](verify.txt) | Журнал сборки и BUILD SUCCESS |
| [junit.xml](junit.xml) | Имена, длительность и результаты 100 тестов |
| [environment.json](environment.json) | Окружение и адрес тестового сервера |
| [ui-results.json](ui-results.json) | Последние видимые ответы операций из сохранённого HTML |
| [run-summary.json](run-summary.json) | Сводка, версия кода и контрольные суммы |

`junit.xml` объединяет отчёты Surefire и Failsafe без системных свойств и
текстовых потоков. В журнале убраны пробелы в конце строк.

## Скриншоты

- [UI-01: чтение после перезагрузки](ui/createsAndReadsTaskAfterReload.png).
- [UI-02: 100 символов и значения по умолчанию](ui/appliesDefaultsToTaskWithMaximumTitleLength.png).
- [UI-03: изменённая задача](ui/replacesTaskAndPersistsChanges.png).
- [UI-04: 404 после удаления и пустой список](ui/deletesTaskAndShowsNotFound.png).
- [UI-05: ошибки ввода и сохранённая задача](ui/rejectsInvalidInputWithoutChangingStoredTask.png).

Это финальные состояния страниц. Промежуточные действия, включая первый
DELETE с ответом 204, доступны в `target/ui-artifacts/<имя метода>/trace.zip`.
[Как открыть трассировку](../README.md#отладка).
