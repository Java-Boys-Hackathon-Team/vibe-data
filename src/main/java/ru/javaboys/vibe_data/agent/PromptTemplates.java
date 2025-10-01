package ru.javaboys.vibe_data.agent;


public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SYSTEM_ROLE = """
            Вы — опытный инженер по производительности Trino + Iceberg и оптимизатор SQL-запросов/DDL.
            Вы должны выдавать валидный SQL для Trino и DDL, совместимый с Iceberg.
            Строго следуйте правилам и отдавайте предпочтение улучшениям, дающим измеримый эффект.
            
            Правила:
            {rules}
            
            Ограничения проекта:
            {catalogSchemaRule}
            """;

    public static final String SYSTEM_ROLE_WIITH_OPTIMIZATIONS = SYSTEM_ROLE + """
            Дополнительно у тебя есть набор рекомендаций по оптимизации, которые нужно всегда учитывать при формировании ответа.
            Эти рекомендации не заменяют твои знания, а являются уточняющими правилами для генерации более качественных решений.
            Список этих рекомендаций:
            {optimizations}
            """;

    public static final String RULES = """
            - Работайте только для Trino с коннектором Iceberg, DDL должен быть валиден для Iceberg.
            - Предпочитайте: партиционирование, ordering, z-ordering, исправление типов данных, устранение лишних колонок, predicate pushdown, join reordering, материализацию CTE через временные таблицы (если применимо), денормализацию при наличии звёздной/снежинки схемы.
            - Всегда используйте полные имена: <каталог>.<схема>.<таблица>.
            - Не нарушайте семантику запросов.
            - Сохраняйте исходный queryid у переписанных запросов.
            - При предложении DDL по возможности делайте их идемпотентными (используйте CREATE SCHEMA IF NOT EXISTS / CREATE TABLE IF NOT EXISTS, если допустимо, либо явно описывайте предположения).
            """;

    public static final String CATALOG_SCHEMA_RULE = """
            ВНИМАНИЕ (ТЗ):
            1) Все команды работы с таблицами должны использовать полный путь <каталог>.<схема>.<таблица>.
            2) В ответе первой DDL-командой должен идти CREATE SCHEMA <каталог>.<новая_схема>.
            3) Все SQL запросы, переносящие данные, и все новые запросы — тоже с полными именами в НОВОЙ схеме.
            """;

    public static final String QUERY_OPTIMIZATION_PROMPT = """
            Контекст:
            - Исходный DDL (все таблицы):
            {original_ddl}
            
            - Уже накопленные изменения DDL на предыдущих шагах:
            {accumulated_ddl}
            
            Задача (итерация):
            Перед тобой один SQL-запрос (Trino/Iceberg), который часто выполняется и затратен.
            Тебе нужно:
            - Проанализировать план (разрешено вызывать инструменты EXPLAIN/ANALYZE).
            - Используй инструмент runReadOnlyQuery для анализа служебных таблиц Trino и получения статистики.
            - Если вызов инструмента завершился ошибкой, игнорируй её и продолжай.
            - Предложить ПЕРЕПИСАННУЮ версию запроса для улучшения производительности.
            - При необходимости — предложить изменения DDL с полными именами.
            - Сохранить идентификатор запроса.
            - Учитывать уже накопленные изменения DDL из предыдущих шагов (их можно дополнять).
            
            Метаданные:
            - queryid: {queryid}
            - runquantity: {runquantity}
            - executiontime: {executiontime}
            
            SQL-запрос, который нужно оптимизировать:
            {query_sql}
            
            Важно:
            - Полные имена таблиц в DDL и в запросе.
            - Не нарушай семантику запроса.
            
            Перед выдачей ответа проверь чек-лист:
            1) Нет ссылок в WHERE на алиасы из того же SELECT.
            2) Любые фильтры по оконным функциям/агрегатам — только QUALIFY или внешний SELECT.
            3) Полные имена таблиц корректны и существуют в текущей схеме (без переноса в новую схему).
            """;

    public static final String MIGRATION_SYNTHESIS_PROMPT = """
            Финальная задача:
            На основе:
            - Исходного DDL:
            {original_ddl}
            
            - Оптимизированного DDL (накопленного):
            {optimized_ddl}
            
            Сгенерируй:
            1) Итоговый набор DDL для новой структуры (перечисление SQL операторов). Первая команда обязательно — CREATE SCHEMA <каталог>.<новая_схема>.
            2) Набор SQL миграций INSERT ... SELECT ... для переноса данных из старой структуры в новую. Везде полные имена.
            3) Никаких комментариев, только данные.
            """;

    // ===== Validation autofix prompts =====
    public static final String VALIDATION_AUTOFIX_SYSTEM = """
            Ты эксперт по SQL для Trino/Iceberg (Trino + Iceberg + S3).
            
            Твоя задача: сделать минимальные правки в DDL, миграциях и запросах так, чтобы они исполнялись (сохраняя структуру и смысл).
            
            Терминология и соответствие ПОЗИЦИЯ-К-ПОЗИЦИИ:
            - ВХОД:  IN_DDL[]         → ВЫХОД: OUT_DDL[]         (OUT_DDL[i] — правка IN_DDL[i])
            - ВХОД:  IN_MIGRATIONS[]  → ВЫХОД: OUT_MIGRATIONS[]  (OUT_MIGRATIONS[i] — правка IN_MIGRATIONS[i])
            - ВХОД:  IN_SQL[]         → ВЫХОД: OUT_SQL[]         (OUT_SQL[i] — правка IN_SQL[i])
            
            Жёсткие правила:
            - Размеры массивов на выходе ТОЧНО равны размерам входных массивов.
            - Порядок сохранён: индекс i на выходе соответствует индексу i на входе.
            - Если элемент корректен — верни его без изменений.
            - НЕЛЬЗЯ упрощать бизнес-логику, менять агрегации или удалять CTE/подзапросы.
            - НЕЛЬЗЯ добавлять заглушки (например, SELECT * FROM ... без смысла).
            - DDL меняй минимально (синтаксис, свойства, кавычки зарезервированных идентификаторов и т.п.).
            
            Формат ответа:
            Верни СТРОГИЙ RFC8259 JSON без Markdown и без комментариев,
            РОВНО со следующими полями верхнего уровня:
            
            {
              "rationale": string,
              "OUT_DDL": string[],
              "OUT_MIGRATIONS": string[],
              "OUT_SQL": string[]
            }
            """;

    public static String buildValidationAutofixUser(
            int attempt,
            String prodJdbcUrl,
            String errorsBlock,
            int inDdlCount, String inDdlJoined,
            int inMigCount, String inMigJoined,
            int inSqlCount, String inSqlJoined
    ) {
        return ("""
                Попытка: %d
                PROD JDBC: %s
                
                Ошибки валидации (если есть):
                %s
                
                ВХОДНЫЕ ДАННЫЕ:
                
                IN_DDL (%d шт, индексированно по порядку):
                %s
                
                IN_MIGRATIONS (%d шт, индексированно по порядку):
                %s
                
                IN_SQL (%d шт, индексированно по порядку):
                %s
                
                ТРЕБОВАНИЯ К ОТВЕТУ:
                - Верни JSON только с полями: rationale, OUT_DDL, OUT_MIGRATIONS, OUT_SQL.
                - Длины массивов: len(OUT_DDL) == %d, len(OUT_MIGRATIONS) == %d, len(OUT_SQL) == %d.
                - OUT_X[i] — это исправленная версия IN_X[i] (позиции 1:1).
                - Правки минимальные и исполняемые.
                - Строгий RFC8259 JSON, без Markdown и без лишнего текста.
                """).formatted(
                attempt,
                String.valueOf(prodJdbcUrl),
                errorsBlock == null ? "—" : errorsBlock,
                inDdlCount, inDdlJoined == null ? "" : inDdlJoined,
                inMigCount, inMigJoined == null ? "" : inMigJoined,
                inSqlCount, inSqlJoined == null ? "" : inSqlJoined,
                inDdlCount, inMigCount, inSqlCount
        );
    }
}
