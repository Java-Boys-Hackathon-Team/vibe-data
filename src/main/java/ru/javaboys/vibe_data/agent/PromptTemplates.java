package ru.javaboys.vibe_data.agent;


public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SYSTEM_ROLE = """
            You are a senior Trino + Iceberg performance engineer and SQL query/DDL optimizer.
            You must produce **valid Trino SQL** and **Iceberg-friendly DDL** only.
            Follow the rules strictly and prefer measurable improvements.
            
            Rules:
            {rules}
            
            Project constraints (RU):
            {catalogSchemaRule}
            """;

    public static final String RULES = """
            - Work for Trino with Iceberg connector only (read-only cluster for analysis, but DDL must be valid Iceberg).
            - Prefer: partitioning, ordering, z-ordering, data-type fixes, column pruning, predicate pushdown, join reordering, CTE materialization via temp tables (if applicable), denormalization when star/snowflake patterns appear.
            - Always use **fully qualified names**: <catalog>.<schema>.<table>.
            - Do not break semantics of queries.
            - Keep original queryid for rewritten queries.
            - When proposing DDL, ensure it is idempotent-safe when possible (use CREATE SCHEMA if not exists / CREATE TABLE if not exists if allowed, or document assumptions).
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
            Перед тобой один SQL-запрос (Trino), который часто выполняется и затратен.
            Тебе нужно:
            1) Проанализировать план (разрешено вызывать инструменты EXPLAIN/ANALYZE).
            2) Предложить ПЕРЕПИСАННУЮ версию запроса для улучшения производительности.
            3) При необходимости — предложить изменения DDL (например: новую денормализованную таблицу, партиционирование, сортировку, материализацию и т.д.) с полными именами.
            4) Сохранить идентификатор запроса.
            5) Учитывать уже накопленные изменения DDL из предыдущих шагов (их можно дополнять).
            
            Метаданные:
            - queryid: {queryid}
            - runquantity: {runquantity}
            - executiontime: {executiontime}
            
            SQL:
            {query_sql}
            
            Ожидаемый строго-структурированный JSON-ответ (без лишних полей) под Java-класс:
            PerQueryOptimizationOutput<
              String queryid;              // тот же, что во входе
              String rewrittenQuery;       // новая версия SQL (Trino)
              List<String> ddlChanges;     // ноль или больше DDL-операторов (CREATE SCHEMA/TABLE/… с полными именами)
              String reasoning;            // краткие причины изменений (до 10 строк)
            >
            Важно:
            - Полные имена таблиц в DDL и в запросе.
            - Не добавляй комментарии вне JSON.
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
            
            Строго выдай JSON под Java-класс:
            FinalMigrationOutput<
              List<String> newDdl;      // полный список DDL; первая команда — CREATE SCHEMA ...
              List<String> migrations;  // список INSERT ... SELECT ... с полными именами
            >
            """;
}
