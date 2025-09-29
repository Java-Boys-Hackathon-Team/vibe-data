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
            Твоя задача — исполняемо исправить ПРЕДОСТАВЛЕННЫЕ НИЖЕ запросы и DDL, сохраняя их структуру и смысл.
            НЕЛЬЗЯ упрощать запросы, удалять CTE/подзапросы, менять агрегации и бизнес-логику.

            ОБЯЗАТЕЛЬНО:
            - Количество запросов в newSql ДОЛЖНО остаться тем же.
            - Порядок сохранён: newSql[i] — это исправленная версия текущего newSql[i].
            - Если запрос корректен — верни его без изменений.
            - Не добавляй «заглушки» вроде SELECT * FROM ...
            - DDL меняй минимально (пример: корректные свойства/синтаксис партиционирования, кавычки для year/month при необходимости).
            Верни ТОЛЬКО JSON по схеме (rationale, newDdl, migrations, newSql), без Markdown и без комментариев.
            """;

    public static String buildValidationAutofixUser(
            int attempt,
            String prodJdbcUrl,
            String errorsBlock,
            int newDdlCount, String newDdl,
            int migCount, String migrations,
            int newSqlCount, String newSql
    ) {
        return ("""
            Попытка: %d
            PROD JDBC: %s

            Ошибки валидации:
            %s

            NEW DDL (%d шт):
            %s

            MIGRATIONS (%d шт):
            %s

            NEW SQL (%d шт, по порядку):
            %s

            Требования к ответу:
            - Верни JSON с полями: rationale, newDdl, migrations, newSql.
            - Размер newSql ДОЛЖЕН быть ровно %d (как сейчас), порядок сохранён.
            - Вноси только минимальные правки, чтобы всё выполнялось.
            - Формат ответа: строгий RFC8259 JSON, без Markdown-блоков и лишнего текста.
            """).formatted(
                attempt,
                String.valueOf(prodJdbcUrl),
                errorsBlock == null ? "—" : errorsBlock,
                newDdlCount, newDdl == null ? "" : newDdl,
                migCount, migrations == null ? "" : migrations,
                newSqlCount, newSql == null ? "" : newSql,
                newSqlCount
        );
    }

    public static String buildValidationExceptionUser(
            int attempt,
            String shortError,
            String prodJdbcUrl,
            int newDdlCount, String newDdl,
            int migCount, String migrations,
            int newSqlCount, String newSql
    ) {
        return ("""
            Попытка: %d
            Ошибка (коротко): %s

            Контекст:
            - prodTrinoJdbcUrl: %s

            NEW DDL (%d шт):
            %s

            MIGRATIONS (%d шт):
            %s

            NEW SQL (%d шт, по порядку):
            %s

            Требования к ответу:
            - Верни JSON с полями: rationale, newDdl, migrations, newSql.
            - Размер newSql ДОЛЖЕН быть ровно %d (как сейчас), порядок сохранён.
            - Вноси только минимальные правки, чтобы всё выполнялось.
            - Формат ответа: строгий RFC8259 JSON, без Markdown-блоков и лишнего текста.
            """).formatted(
                attempt,
                String.valueOf(shortError),
                String.valueOf(prodJdbcUrl),
                newDdlCount, newDdl == null ? "" : newDdl,
                migCount, migrations == null ? "" : migrations,
                newSqlCount, newSql == null ? "" : newSql,
                newSqlCount
        );
    }
}
