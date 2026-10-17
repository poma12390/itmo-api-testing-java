# Чек-лист проверки приложения задач

Дата: 17.10.2026. Базовая версия: `5c663ea7eadbbd830f438680f556f13639cfaecc`. Окружение, источники требований и подготовка — в [отчёте](README.md).

**102 пункта: 93 Pass, 6 Fail, 3 Not run. Выполнено 99. Подтверждено 3 дефекта.**

Pass — получен ожидаемый результат; Fail — зафиксировано отклонение; Not run — проверка не выполнялась. Blocked применялся бы при препятствии к выполнению, N/A — при неприменимости; в этом прогоне таких строк нет. P1 — сначала: основные операции и целостность данных; P2 — следующий приоритет. Это приоритет **проверок**, а не severity дефектов.

Общие предусловия: запущен отдельный локальный экземпляр; нет параллельных пользователей; HTTP-запросы имеют `Accept: application/json`, запросы с телом — `Content-Type: application/json`, если строка не задаёт иное. Для PUT сначала создаётся существующая задача. ID берётся из ответа, не задаётся константой. После негативных POST/PUT сравнивается весь список до и после запроса. Для пагинации подготовлена 21 задача в известном порядке.

Строки с POST/PUT выполнены отдельно для обоих методов. В счётчике это один пункт с двумя вариантами; число HTTP-запросов не используется как число проверок. Строки ID выполнены для GET, PUT и DELETE. Отрицательный результат любой вариации даёт Fail всему пункту.

Для HTTP-пунктов фактические запросы и ответы находятся в [http-checks.json](evidence/http-checks.json): `checks` содержит ID и `eventRange`, а `events` — события с этим ID. Диапазон индексов начинается с нуля, обе границы включены; в нём есть подготовка и проверки состояния. У Fail ниже есть прямая ссылка на баг-репорт. Все результаты, включая дополнительные пункты, доступны в [checklist-results.json](evidence/checklist-results.json).

## Веб-интерфейс

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| UI-01 / R-13 | P2 | Открыть Swagger UI | Видны Tasks API 1.0.0, OAS 3.1 и все пять операций | Pass | [Факт](evidence/ui/reload.txt) |
| UI-02 / R-13 | P2 | Раскрыть POST, свернуть его и раскрыть GET | Открывается выбранная операция; остальные блоки доступны | Pass | [Факт](evidence/ui/read.txt) |
| UI-03 / R-13 | P2 | Нажать Try it out, затем Cancel в POST | Редактор и Execute доступны в режиме редактирования; после Cancel возвращается Try it out, редактор скрыт | Pass | [Факт](evidence/ui/cancel.txt) |
| UI-04 / R-13 | P2 | Создать {"title":"Проверка через браузер","priority":2,"completed":false} | В Server response показан 201; возвращены id и переданные поля | Pass | [Факт](evidence/ui/create.txt) |
| UI-05 / R-13 | P2 | После POST проверить Curl, Request URL и Response headers | Метод POST; путь /tasks; application/json; Location указывает на созданный id | Pass | [Факт](evidence/ui/create.txt) |
| UI-06 / R-13 | P2 | GET созданной через браузер задачи (в прогоне id=173) | В интерфейсе показан 200 и сохранённые значения | Pass | [Факт](evidence/ui/read.txt) |
| UI-07 / R-13 | P2 | PUT для того же id: title="Обновлено в браузере", priority=5, completed=true | В интерфейсе показан 200; id сохранён, поля заменены | Pass | [Факт](evidence/ui/update.txt) |
| UI-08 / R-13 | P2 | Удалить созданную задачу через DELETE | В интерфейсе показан 204; секции тела ответа нет | Pass | [Факт](evidence/ui/delete.txt) |
| UI-09 / R-13 | P2 | Повторить DELETE той же задачи | В интерфейсе показан 404 и detail=Task not found; форма остаётся доступной | Pass | [Факт](evidence/ui/not-found.txt) |
| UI-10 / R-13 | P2 | В POST отправить {} | В интерфейсе показан 422, detail=Validation failed, field=title | Pass | [Факт](evidence/ui/validation.txt) |
| UI-11 / R-13 | P2 | Раскрыть схему TaskRequest | title отмечен обязательным; длина 1–100, pattern=\S; priority 1–5; completed boolean; лишние поля запрещены | Pass | [Факт](evidence/ui/schema.txt) |
| UI-12 / R-13 | P2 | Перезагрузить страницу Swagger UI | Контракт загружается повторно; все операции и формы доступны | Pass | [Факт](evidence/ui/reload.txt) |
| UI-13 / R-13 | P2 | В GET /tasks задать limit=1, offset=1 и выполнить запрос после перезагрузки страницы | Request URL содержит оба параметра; показан 200 и один второй элемент списка | Pass | [Факт](evidence/ui/list.txt) |

## Контракт

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| HTTP-01 / R-01 | P1 | Открыть /openapi.json и сравнить с файлом проекта | 200; OpenAPI 3.1; пять операций; контракт совпадает с сохранённым | Pass | [HTTP](evidence/http-checks.json) |

## Жизненный цикл

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| CRUD-01 / R-02 | P1 | Получить список после удаления всех тестовых задач | 200 и пустой массив | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-02 / R-02 | P1 | Создать задачу с title, priority=2, completed=true | 201; все поля сохранены; положительный id; Location ведёт к этой задаче | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-03 / R-03 | P1 | Создать задачу только с title | 201; priority=3, completed=false | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-04 / R-02 | P1 | Создать две задачи с одинаковым названием | Обе созданы; id различаются; обе доступны | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-05 / R-02 | P1 | Прочитать существующую задачу по id | 200; значения совпадают с сохранёнными | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-06 / R-04 | P1 | Заменить все поля задачи; проверить соседнюю задачу | 200; id прежний; новые поля сохранены; соседняя задача не меняется | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-07 / R-03,R-04 | P1 | PUT только с title для задачи с priority=2, completed=true | 200; priority сброшен в 3, completed в false; id сохранён | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-08 / R-04 | P2 | Дважды выполнить одинаковый PUT | Оба ответа 200; итоговое состояние одинаково; число задач не растёт | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-09 / R-05 | P1 | Удалить существующую задачу | 204; тело пустое; в списке задачи нет | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-10 / R-05,R-09 | P1 | GET удалённой задачи | 404; detail=Task not found | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-11 / R-05,R-09 | P2 | Повторно удалить ту же задачу | 404; остальные задачи не меняются | Pass | [HTTP](evidence/http-checks.json) |
| CRUD-12 / R-04,R-09 | P1 | PUT удалённой задачи | 404; задача не создаётся; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |

## Название

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| TITLE-01 / R-06 | P1 | Название из одного символа; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-02 / R-06 | P1 | Название из 99 символов кириллицы; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-03 / R-06 | P1 | Название из 100 символов кириллицы; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-04 / R-06 | P1 | Пустое название; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-05 / R-06 | P1 | Название из 101 символа; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-06 / R-06 | P1 | Поле title отсутствует; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-07 / R-06 | P1 | title=null; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-08 / R-06 | P1 | Название только из ASCII-пробелов, табуляции и перевода строки; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-09 / R-06 | P1 | title — число; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-10 / R-06 | P1 | title — boolean; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-11 / R-06 | P1 | title — массив; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-12 / R-06 | P1 | title — объект; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-13 / R-06 | P1 | Смешанное название с кириллицей и emoji; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-14 / R-06 | P1 | Название из 50 emoji U+1F600; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-15 / R-06 | P1 | Название из 51 emoji U+1F600; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Fail | [BUG-003](bugs/BUG-003.md) |
| TITLE-16 / R-06 | P1 | Название из 100 emoji U+1F600; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Fail | [BUG-003](bugs/BUG-003.md) |
| TITLE-17 / R-06 | P1 | Название из 101 emoji U+1F600; отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| TITLE-18 / R-06 | P1 | Название только из U+00A0 (неразрывный пробел); отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Fail | [BUG-002](bugs/BUG-002.md) |
| TITLE-19 / R-06 | P1 | Название только из U+2003 (EM SPACE); отдельно POST и PUT существующей задачи | 422; field=title; хранилище не меняется | Fail | [BUG-002](bugs/BUG-002.md) |
| TITLE-20 / R-06 | P1 | Название с пробелами вокруг непустого текста; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |

## Приоритет

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| PRIORITY-01 / R-07 | P1 | Нижняя граница priority=1; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-02 / R-07 | P1 | Верхняя граница priority=5; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-03 / R-07 | P1 | priority=0; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-04 / R-07 | P1 | priority=6; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-05 / R-07 | P1 | Дробный priority=2.5; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-06 / R-07 | P1 | Строка вместо priority; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-07 / R-07 | P1 | Boolean вместо priority; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-08 / R-07 | P1 | priority=null; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-09 / R-07 | P1 | Массив вместо priority; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-10 / R-07 | P1 | Объект вместо priority; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-11 / R-07 | P1 | priority больше int32; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-12 / R-07 | P1 | Отрицательный priority=-1; отдельно POST и PUT существующей задачи | 422; field=priority; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-13 / R-07 | P2 | POST и PUT с числом priority=4.0 | POST 201 / PUT 200; математически целое число принято без изменения значения | Pass | [HTTP](evidence/http-checks.json) |
| PRIORITY-14 / R-07 | P2 | POST и PUT с числом priority=1e0 | POST 201 / PUT 200; математически целое число принято без изменения значения | Pass | [HTTP](evidence/http-checks.json) |

## Выполнение

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| BOOL-01 / R-08 | P1 | completed=true; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-02 / R-08 | P1 | completed=false; отдельно POST и PUT существующей задачи | POST 201 / PUT 200; значения точно сохранены; GET возвращает те же данные | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-03 / R-08 | P1 | completed="false"; отдельно POST и PUT существующей задачи | 422; field=completed; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-04 / R-08 | P1 | completed=1; отдельно POST и PUT существующей задачи | 422; field=completed; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-05 / R-08 | P1 | completed=null; отдельно POST и PUT существующей задачи | 422; field=completed; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-06 / R-08 | P1 | completed=[]; отдельно POST и PUT существующей задачи | 422; field=completed; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| BOOL-07 / R-08 | P1 | completed={}; отдельно POST и PUT существующей задачи | 422; field=completed; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |

## JSON и HTTP

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| JSON-01 / R-10 | P1 | Передать лишнее поле id=10; отдельно POST и PUT существующей задачи | 422; field=id; хранилище не меняется | Pass | [HTTP](evidence/http-checks.json) |
| JSON-02 / R-10 | P1 | Оборванный JSON; отдельно POST и PUT | 400; field=body; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| JSON-03 / R-10 | P1 | Отсутствующее тело; отдельно POST и PUT | 400; field=body; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| JSON-04 / R-10 | P1 | Невалидный UTF-8; отдельно POST и PUT | 400; field=body; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| JSON-05 / R-10 | P1 | Второй JSON-объект после первого; отдельно POST и PUT | 400; field=body; данные не меняются | Fail | [BUG-001](bugs/BUG-001.md) |
| JSON-06 / R-10 | P1 | Мусор после JSON; отдельно POST и PUT | 400; field=body; данные не меняются | Fail | [BUG-001](bugs/BUG-001.md) |
| JSON-07 / R-10 | P1 | Допустимые пробелы после JSON; отдельно POST и PUT | POST 201 / PUT 200; пробелы вне объекта допустимы | Pass | [HTTP](evidence/http-checks.json) |
| HTTP-02 / A-04 | P2 | POST с Content-Type: text/plain | 415; новая задача не появляется (дополнительная HTTP-проверка) | Pass | [HTTP](evidence/http-checks.json) |
| HTTP-03 / R-10 | P2 | PATCH для коллекции /tasks | 405; Allow содержит GET и POST; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| HTTP-04 / R-10 | P2 | PATCH существующей задачи | 405; Allow содержит DELETE, GET, PUT; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |

## Пагинация

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| PAGE-01 / R-11 | P2 | GET /tasks; Параметры отсутствуют: значения по умолчанию; создана 21 задача | 200; 20 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-02 / R-11 | P2 | GET /tasks?limit=1; limit=1; создана 21 задача | 200; 1 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-03 / R-11 | P2 | GET /tasks?limit=100; limit=100; создана 21 задача | 200; 21 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-04 / R-11 | P2 | GET /tasks?limit=0; limit=0; создана 21 задача | 422; field=limit; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-05 / R-11 | P2 | GET /tasks?limit=101; limit=101; создана 21 задача | 422; field=limit; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-06 / R-11 | P2 | GET /tasks?offset=0; offset=0; создана 21 задача | 200; 20 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-07 / R-11 | P2 | GET /tasks?limit=1&offset=1; Смещение на вторую задачу; создана 21 задача | 200; 1 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-08 / R-11 | P2 | GET /tasks?offset=21; Смещение равно числу задач; создана 21 задача | 200; 0 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-09 / R-11 | P2 | GET /tasks?offset=2147483647; Максимальный offset int32; создана 21 задача | 200; 0 задач в порядке создания | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-10 / R-11 | P2 | GET /tasks?offset=-1; offset=-1; создана 21 задача | 422; field=offset; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-11 / R-11 | P2 | GET /tasks?limit=abc; Нечисловой limit; создана 21 задача | 422; field=limit; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-12 / R-11 | P2 | GET /tasks?offset=abc; Нечисловой offset; создана 21 задача | 422; field=offset; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-13 / R-11 | P2 | GET /tasks?limit=2147483648; Переполнение limit int32; создана 21 задача | 422; field=limit; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-14 / R-11 | P2 | GET /tasks?offset=2147483648; Переполнение offset int32; создана 21 задача | 422; field=offset; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| PAGE-15 / R-11 | P2 | Изменить первую и удалить вторую задачу; получить список | Изменённая сохраняет место; удалённая отсутствует; остальные в прежнем порядке | Pass | [HTTP](evidence/http-checks.json) |

## Идентификатор

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| ID-01 / R-09 | P1 | GET, PUT и DELETE /tasks/0 | 422; field=taskId; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| ID-02 / R-09 | P1 | GET, PUT и DELETE /tasks/-1 | 422; field=taskId; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| ID-03 / R-09 | P1 | GET, PUT и DELETE /tasks/abc | 422; field=taskId; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| ID-04 / R-09 | P1 | GET, PUT и DELETE /tasks/1.5 | 422; field=taskId; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| ID-05 / R-09 | P1 | GET, PUT и DELETE /tasks/9223372036854775808 | 422; field=taskId; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |
| ID-06 / R-09 | P1 | GET, PUT и DELETE /tasks/9223372036854775807 | 404; detail=Task not found; данные не меняются | Pass | [HTTP](evidence/http-checks.json) |

## Хранение

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| STORE-01 / R-12 | P2 | Создать маркер; остановить и запустить тот же JAR; получить список и прежний id | После запуска 200 []; прежний id возвращает 404: данные хранятся только в памяти | Pass | [Факт](evidence/lifecycle.json) |

## Дополнительное покрытие

| ID / требование | Приоритет | Проверка и данные | Ожидаемый результат | Результат | Доказательство / дефект |
| --- | --- | --- | --- | --- | --- |
| EXT-01 / A-01 | P2 | Повторить основной UI-сценарий в независимых Chrome и Firefox | Создание, чтение, изменение и удаление доступны в обоих браузерах | Not run | В прогоне использован только встроенный браузер; независимые браузеры не были доступны |
| EXT-02 / A-02 | P2 | Проверить формы и ответы при ширине экрана 360 CSS px | Поля и кнопки доступны; результат запроса читается; элементы не перекрываются | Not run | Прогон выполнен при viewport 1280×720; мобильная компоновка не проверена |
| EXT-03 / A-03 | P2 | Пройти сценарий POST только клавиатурой: Tab, Shift+Tab, Enter | Фокус виден; все поля и Execute доступны; отсутствует ловушка фокуса | Not run | Клавиатурный сценарий и проверка средствами экранного диктора не выполнялись |

## Что не включено

Регистрация, роли, поиск, загрузка файлов и оплата отсутствуют в приложении и требованиях. Они не превращены в фиктивные проверки или дефекты. Нагрузочное тестирование и полный аудит безопасности требуют отдельного плана и критериев. Три EXT-пункта — предложенное расширение покрытия, а не требования, приписанные курсу.
