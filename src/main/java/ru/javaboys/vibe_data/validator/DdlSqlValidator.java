package ru.javaboys.vibe_data.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.jsonb.DdlStatement;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;
import ru.javaboys.vibe_data.llm.LlmRequest;
import ru.javaboys.vibe_data.llm.LlmService;
import ru.javaboys.vibe_data.validator.api.SqlFile;
import ru.javaboys.vibe_data.validator.api.ValidationModels;
import ru.javaboys.vibe_data.agent.PromptTemplates;
import ru.javaboys.vibe_data.dto.AutoFixOutput;
import ru.javaboys.vibe_data.dto.ValidationPatch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * Алгоритм:
 * 0) Нормализация каталогов (source -> local) во всех входных SQL/DDL
 * 0.1) Генерим суффикс прогона (timestamp) и переписываем ВСЕ FQTN на схемы с суффиксом: <catalog>.<schema> -> <catalog>.<schema>_<ts>
 * 1) применяем старые DDL на локальном Trino
 * 2) для созданных таблиц — загружаем данные из PROD (читаем из sourceCatalog, пишем в local c суффиксом схемы)
 * 3) выполняем старые SQL-запросы на локальных таблицах → собираем результаты
 * 4) создаём новую схему/каталог (с суффиксом)
 * 5) применяем новые DDL
 * 6) применяем миграции
 * 7) выполняем новые SQL-запросы → собираем результаты
 * 8) сравниваем результаты из (3) и (7)
 */
@Slf4j
@Service
public class DdlSqlValidator {

    private static final Set<String> RESERVED = Set.of("year", "month");
    private final Map<String, Object> stageState = new HashMap<>();
    private static final int SAMPLE_LIMIT = 50;

    private final JdbcTemplate localTrino;

    @Value("${trino.local.catalog}")
    private String localCatalogName;

    private final LlmService llmService;

    public DdlSqlValidator(@Qualifier("trinoLocalJdbcTemplate") JdbcTemplate localTrino, LlmService llmService) {
        this.localTrino = localTrino;
        this.llmService = llmService;
    }

    /**
     * Входная точка. Оркестрирует все шаги и возвращает агрегированный отчёт.
     */
    @Transactional
    public ValidatedArtifacts validateFinalArtifacts(
            Task task,
            List<SqlBlock> finalDdl,
            List<SqlBlock> migrations,
            List<RewrittenQuery> finalQueries
    ) {
        var payload = task.getInput().getPayload();

        ValidationModels.ValidationRequest vreq = ValidationModels.ValidationRequest.builder()
                .prodTrinoJdbcUrl(payload.getUrl())
                .oldDdl(toSqlFiles(payload.getDdl().stream().map(DdlStatement::getStatement).toList()))
                .oldSql(toSqlFiles(payload.getQueries().stream().map(QueryInput::getQuery).toList()))
                .newDdl(toSqlFilesFromBlocks(finalDdl))
                .migrations(toSqlFilesFromBlocks(migrations))
                .newSql(toSqlFiles(finalQueries.stream().map(RewrittenQuery::getQuery).toList()))
                .build();

        ValidationModels.ValidationReport vrep = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                vrep = run(vreq);

                if (vrep.getStatus() == ValidationModels.ValidationStatus.OK) {
                    log.info("Validation succeeded on attempt {}", attempt);
                    return new ValidatedArtifacts(finalDdl, migrations, finalQueries, vrep);
                }

                // Есть структурированные ошибки — отправим их в LLM и попробуем автоисправление
                log.warn("Validation attempt {} finished with ERROR ({} issues)", attempt, vrep.getErrors().size());

                var patch = sendValidationErrorsToLlm(task, vreq, vrep.getErrors(), attempt); // ваш существующий метод-обёртка

                if (patch != null) {
                    // Применяем патч: LLM мог вернуть новые DDL/миграции/SQL
                    if (patch.getNewDdl() != null && !patch.getNewDdl().isEmpty()) {
                        finalDdl = toSqlBlocks(patch.getNewDdl());
                    }
                    if (patch.getMigrations() != null && !patch.getMigrations().isEmpty()) {
                        migrations = toSqlBlocks(patch.getMigrations());
                    }
                    if (patch.getNewSql() != null && !patch.getNewSql().isEmpty()) {
                        finalQueries = patch.getNewSql().stream()
                                .map(sql -> RewrittenQuery.builder().queryid(null).query(sql).build())
                                .toList();
                    }

                    // Пересобираем запрос на валидацию после автоисправлений
                    vreq = ValidationModels.ValidationRequest.builder()
                            .prodTrinoJdbcUrl(resolveProdJdbcUrl(task))
                            .oldDdl(toSqlFiles(payload.getDdl().stream().map(DdlStatement::getStatement).toList()))
                            .oldSql(toSqlFiles(payload.getQueries().stream().map(QueryInput::getQuery).toList()))
                            .newDdl(toSqlFilesFromBlocks(finalDdl))
                            .migrations(toSqlFilesFromBlocks(migrations))
                            .newSql(toSqlFiles(finalQueries.stream().map(RewrittenQuery::getQuery).toList()))
                            .build();

                    // продолжаем цикл (следующая попытка)
                    continue;
                }

                // Нечего применить — считаем попытку неуспешной
                if (attempt == 3) {
                    log.error("Validation failed after {} attempts (no applicable patch)", attempt);
                    return new ValidatedArtifacts(finalDdl, migrations, finalQueries, vrep);
                }

            } catch (Exception e) {
                // airbag на случай непредвиденного исключения
                sendValidationErrorsToLlm(task, vreq, e, attempt);
                if (attempt == 3) {
                    log.error("Validation failed after {} attempts due to unexpected exception", attempt, e);
                    throw e;
                }
            }
        }

        // сюда обычно не дойдём; возвращаем текущее состояние
        return new ValidatedArtifacts(finalDdl, migrations, finalQueries, vrep);
    }

    private ValidationModels.ValidationReport run(ValidationModels.ValidationRequest request) {
        Objects.requireNonNull(request, "request is null");
        log.info("Start validation pipeline");

        List<ValidationModels.StageResult> stages = new ArrayList<>();
        List<ValidationModels.ValidationError> errors = new ArrayList<>();

        String sourceCatalog = detectSourceCatalog(request);
        String localCatalog = resolveLocalCatalog();

        String runSuffix = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        log.info("Run schema suffix: _{}", runSuffix);

        ValidationModels.ValidationRequest normalized =
                addSchemaSuffixForCatalog(rewriteRequestCatalogs(request, sourceCatalog, localCatalog), localCatalog, runSuffix);

        ensureSchemasExist(normalized);

        // 1) OLD DDL
        stageWrap(stages, errors, ValidationModels.StageName.VALIDATE_OLD_DDL, () -> {
            List<String> createdOldTables = applyDdlOnLocal(normalized.getOldDdl(), errors, ValidationModels.StageName.VALIDATE_OLD_DDL);
            stageState.put("createdOldTables", createdOldTables);
            return "Applied old DDL locally for tables: " + createdOldTables;
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 2) SAMPLE
        stageWrap(stages, errors, ValidationModels.StageName.SAMPLE, () -> {
            @SuppressWarnings("unchecked")
            List<String> createdOldTables =
                    (List<String>) stageState.getOrDefault("createdOldTables", List.of());
            if (createdOldTables.isEmpty()) {
                log.warn("No createdOldTables found; skipping sample load");
                return "No tables to load sample for";
            }
            loadDataForTablesFromProdSafe(
                    normalized.getProdTrinoJdbcUrl(),
                    createdOldTables,
                    sourceCatalog, localCatalog, runSuffix, errors
            );
            return "Loaded sample data (" + SAMPLE_LIMIT + " rows per table) from PROD";
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 3) RUN OLD SQL
        stageWrap(stages, errors, ValidationModels.StageName.RUN_OLD_SQL, () -> {
            List<QueryResult> oldResults = runQueriesOnLocal("OLD", normalized.getOldSql(), ValidationModels.StageName.RUN_OLD_SQL, errors);
            stageState.put("oldResults", oldResults); // см. private final Map<String,Object> stageState = new HashMap<>();
            return "Executed old queries: " + oldResults.stream().map(q -> q.name).toList();
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 4) NEW SCHEMA (опционально)
        createNewSchemaIfNeeded(normalized.getNewDdl());

        // 5) NEW DDL
        stageWrap(stages, errors, ValidationModels.StageName.DEPLOY_NEW_DDL, () -> {
            List<String> createdNewTables = applyDdlOnLocal(normalized.getNewDdl(), errors, ValidationModels.StageName.DEPLOY_NEW_DDL);
            return "Applied new DDL locally for tables: " + createdNewTables;
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 6) MIGRATIONS
        stageWrap(stages, errors, ValidationModels.StageName.APPLY_MIGRATIONS, () -> {
            applyMigrationsOnLocalSafe(normalized.getMigrations(), errors);
            return "Applied migrations";
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 7) RUN NEW SQL
        stageWrap(stages, errors, ValidationModels.StageName.RUN_NEW_SQL, () -> {
            List<QueryResult> newResults = runQueriesOnLocal("NEW", normalized.getNewSql(), ValidationModels.StageName.RUN_NEW_SQL, errors);
            stageState.put("newResults", newResults);
            return "Executed new queries: " + newResults.stream().map(q -> q.name).toList();
        });
        if (hasErrors(stages)) {
            return buildReport(stages, errors);
        }

        // 8) COMPARE
        stageWrap(stages, errors, ValidationModels.StageName.COMPARE, () -> {
            List<QueryResult> oldResults = (List<QueryResult>) stageState.getOrDefault("oldResults", List.of());
            List<QueryResult> newResults = (List<QueryResult>) stageState.getOrDefault("newResults", List.of());
            ComparisonReport comparison = compareResults(oldResults, newResults);
            return "Comparison status=" + comparison.status + ", rowsDiff=" + comparison.diffRows + ", colsDiff=" + comparison.diffCols;
        });

        return buildReport(stages, errors);
    }

    // вспомогательное

    private boolean hasErrors(List<ValidationModels.StageResult> stages) {
        ValidationModels.StageResult last = stages.get(stages.size() - 1);
        return last.getStatus() == ValidationModels.StageStatus.ERROR;
    }

    private ValidationModels.ValidationReport buildReport(List<ValidationModels.StageResult> stages,
                                                          List<ValidationModels.ValidationError> errors) {
        ValidationModels.ValidationStatus overall = errors.isEmpty()
                ? ValidationModels.ValidationStatus.OK
                : ValidationModels.ValidationStatus.ERROR;

        List<QueryResult> oldResults = (List<QueryResult>) stageState.getOrDefault("oldResults", List.of());
        List<QueryResult> newResults = (List<QueryResult>) stageState.getOrDefault("newResults", List.of());

        return ValidationModels.ValidationReport.builder()
                .status(overall)
                .stages(stages)
                .errors(errors)
                .artifacts(ValidationModels.ArtifactsInfo.builder()
                        .oldResults(oldResults.stream().map(r -> r.name).toList())
                        .newResults(newResults.stream().map(r -> r.name).toList())
                        .diffs(new ArrayList<>())
                        .build())
                .storageInfo(null)
                .build();
    }

    /**
     * Определяем исходный каталог (первый встреченный <catalog>.<schema>.<table> в DDL/SQL).
     */
    protected String detectSourceCatalog(ValidationModels.ValidationRequest request) {
        java.util.function.Function<List<SqlFile>, String> scan = list -> {
            if (list == null) return null;
            for (SqlFile f : list) {
                String c = firstCatalogInSql(f.getContent());
                if (c != null) return c;
            }
            return null;
        };
        String c = scan.apply(request.getOldDdl());
        if (c == null) c = scan.apply(request.getOldSql());
        if (c == null) c = scan.apply(request.getNewDdl());
        if (c == null) c = scan.apply(request.getMigrations());
        if (c == null) c = scan.apply(request.getNewSql());
        return c; // может быть null — тогда переписывание по каталогу не выполняем
    }

    /**
     * Берём локальный каталог из property, валидируем.
     */
    protected String resolveLocalCatalog() {
        if (isBlank(localCatalogName)) return null;
        return localCatalogName.trim();
    }

    /**
     * Переписываем каталоги во всех списках SQL/DDL: sourceCatalog -> localCatalog.
     */
    protected ValidationModels.ValidationRequest rewriteRequestCatalogs(ValidationModels.ValidationRequest req, String sourceCatalog, String localCatalog) {
        if (isBlank(sourceCatalog) || isBlank(localCatalog) || sourceCatalog.equalsIgnoreCase(localCatalog)) return req;
        ValidationModels.ValidationRequest.ValidationRequestBuilder b = ValidationModels.ValidationRequest.builder()
                .prodTrinoJdbcUrl(req.getProdTrinoJdbcUrl())
                .oldDdl(rewriteListCatalog(req.getOldDdl(), sourceCatalog, localCatalog))
                .oldSql(rewriteListCatalog(req.getOldSql(), sourceCatalog, localCatalog))
                .newDdl(rewriteListCatalog(req.getNewDdl(), sourceCatalog, localCatalog))
                .migrations(rewriteListCatalog(req.getMigrations(), sourceCatalog, localCatalog))
                .newSql(rewriteListCatalog(req.getNewSql(), sourceCatalog, localCatalog));
        return b.build();
    }

    /**
     * После переписывания каталога — добавляем суффикс к схемам в целевом каталоге.
     */
    protected ValidationModels.ValidationRequest addSchemaSuffixForCatalog(ValidationModels.ValidationRequest req, String targetCatalog, String runSuffix) {
        ValidationModels.ValidationRequest.ValidationRequestBuilder b = ValidationModels.ValidationRequest.builder()
                .prodTrinoJdbcUrl(req.getProdTrinoJdbcUrl())
                .oldDdl(rewriteListSchemaSuffix(req.getOldDdl(), targetCatalog, runSuffix))
                .oldSql(rewriteListSchemaSuffix(req.getOldSql(), targetCatalog, runSuffix))
                .newDdl(rewriteListSchemaSuffix(req.getNewDdl(), targetCatalog, runSuffix))
                .migrations(rewriteListSchemaSuffix(req.getMigrations(), targetCatalog, runSuffix))
                .newSql(rewriteListSchemaSuffix(req.getNewSql(), targetCatalog, runSuffix));
        return b.build();
    }

    protected List<SqlFile> rewriteListCatalog(List<SqlFile> list, String sourceCatalog, String localCatalog) {
        if (list == null) return List.of();
        return list.stream()
                .map(sf -> new SqlFile(rewriteSqlCatalogs(sf.getContent(), sourceCatalog, localCatalog)))
                .collect(Collectors.toList());
    }

    protected List<SqlFile> rewriteListSchemaSuffix(List<SqlFile> list, String targetCatalog, String runSuffix) {
        if (list == null) return List.of();
        return list.stream()
                .map(sf -> new SqlFile(addSchemaSuffixToSql(sf.getContent(), targetCatalog, runSuffix)))
                .collect(Collectors.toList());
    }

    /**
     * Заменяем точное вхождение `<source>.<...>` на `<local>.<...>`.
     */
    protected String rewriteSqlCatalogs(String sql, String sourceCatalog, String localCatalog) {
        if (isBlank(sql)) return sql;
        String pattern = "(?i)\\b" + Pattern.quote(sourceCatalog) + "\\.";
        return sql.replaceAll(pattern, localCatalog + ".");
    }

    /**
     * Добавляем суффикс к схемам в FQTN ТОЛЬКО для заданного каталога (идемпотентно).
     */
    protected String addSchemaSuffixToSql(String sql, String targetCatalog, String runSuffix) {
        if (isBlank(sql) || isBlank(targetCatalog) || isBlank(runSuffix)) return sql;
        // Найти все FQTN вида catalog.schema.table. Если catalog == targetCatalog -> schema += _<suffix> (если ещё не добавлен)
        StringBuffer out = new StringBuffer();
        Matcher m = Pattern.compile("(?i)\\b([a-z0-9_]+)\\.([a-z0-9_]+)\\.([a-z0-9_]+)\\b").matcher(sql);
        while (m.find()) {
            String catalog = m.group(1);
            String schema = m.group(2);
            String table = m.group(3);
            if (catalog.equalsIgnoreCase(targetCatalog) && !schema.toLowerCase(Locale.ROOT).endsWith("_" + runSuffix.toLowerCase(Locale.ROOT))) {
                String replacement = catalog + "." + schema + "_" + runSuffix + "." + table;
                m.appendReplacement(out, Matcher.quoteReplacement(replacement));
            } else {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Возвращает первый встретившийся catalog из fully-qualified имён.
     */
    protected String firstCatalogInSql(String sql) {
        if (isBlank(sql)) return null;
        Pattern p = Pattern.compile("(?i)\\b([a-z0-9_]+)\\.[a-z0-9_]+\\.[a-z0-9_]+\\b");
        Matcher m = p.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 4) Создать новые схемы — уже создаём точечно перед DDL, оставлено для читабельности.
     */
    protected void createNewSchemaIfNeeded(List<SqlFile> newDdl) {
        log.debug("Ensuring new schema exists (no-op here)");
    }

    /**
     * 5) Применяем новые DDL.
     */

    /**
     * 8) Сравнение результатов — заглушка.
     */
    protected ComparisonReport compareResults(List<QueryResult> oldResults, List<QueryResult> newResults) {
        return new ComparisonReport("NOT_IMPLEMENTED", 0, 0);
    }

    // ===== ВСПОМОГАТЕЛЬНОЕ =====

    /**
     * Regex для вытаскивания fully-qualified table name из DDL: CREATE TABLE [IF NOT EXISTS] <catalog>.<schema>.<table> ( ...
     */
    private static final Pattern CREATE_TABLE_NAME = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z0-9_]+\\.[a-z0-9_]+\\.[a-z0-9_]+)\\s*\\("
    );

    protected Optional<String> extractTableName(String ddl) {
        Matcher m = CREATE_TABLE_NAME.matcher(ddl);
        if (m.find()) {
            return Optional.ofNullable(m.group(1));
        }
        return Optional.empty();
    }

    /**
     * Меняем FQTN ЛОКАЛЬНОЙ таблицы (localCatalog.schema_<suffix>.table) на PROD FQTN (sourceCatalog.schema.table).
     * Если схема уже без суффикса — просто заменим каталог.
     */
    protected String toProdFqtn(String localFqtn, String sourceCatalog, String localCatalog, String runSuffix) {
        if (isBlank(localFqtn) || isBlank(sourceCatalog) || isBlank(localCatalog)) return localFqtn;
        String[] p = localFqtn.split("\\.");
        if (p.length < 3) return localFqtn;
        String catalog = p[0];
        String schema = p[1];
        String table = p[2];
        if (catalog.equalsIgnoreCase(localCatalog)) {
            String originalSchema = schema;
            String suffix = "_" + runSuffix;
            if (schema.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
                originalSchema = schema.substring(0, schema.length() - suffix.length());
            }
            return sourceCatalog + "." + originalSchema + "." + table;
        }
        return localFqtn;
    }

    protected boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    protected String left(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len) + "...";
    }

    /**
     * Санитайзим DDL: снимаем ';' и экранируем зарезервированные идентификаторы-колонки.
     */
    protected String sanitizeDdl(String sql) {
        if (isBlank(sql)) return sql;
        String cleaned = sql.trim();
        if (cleaned.endsWith(";")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        /*for (String kw : RESERVED) {
            cleaned = cleaned.replaceAll(
                    "(?i)(?<!\\\")\\b" + Pattern.quote(kw) + "\\b(?!\\\")",
                    "\"" + kw + "\""
            );
        }*/
        return cleaned;
    }

    /**
     * Создаёт схему, если она отсутствует. Пример: iceberg.public_20250929_001200
     */
    protected void createSchemaIfMissing(String catalog, String schema) {
        String sql = String.format("CREATE SCHEMA IF NOT EXISTS %s.%s", catalog, schema);
        log.info("Ensuring schema exists: {}", sql);
        localTrino.execute(sql);
    }

    /**
     * Извлекает catalog и schema из имени таблицы вида catalog.schema.table.
     */
    protected String[] parseCatalogAndSchema(String fqtn) {
        if (isBlank(fqtn)) return null;
        String[] parts = fqtn.split("\\.");
        if (parts.length < 3) return null;
        return new String[]{parts[0], parts[1]};
    }

    /**
     * Инфа о колонке локальной таблицы.
     */
    static class ColumnInfo {
        final String name;
        final String dataType; // как отдаёт information_schema, в lower-case

        ColumnInfo(String name, String dataType) {
            this.name = name;
            this.dataType = dataType == null ? "" : dataType.toLowerCase();
        }
    }

    /**
     * Разбор catalog.schema.table
     */
    static class TableRef {
        final String catalog, schema, table;

        TableRef(String c, String s, String t) {
            this.catalog = c;
            this.schema = s;
            this.table = t;
        }

        static TableRef parse(String fqtn) {
            String[] p = fqtn.split("\\.");
            if (p.length < 3) throw new IllegalArgumentException("Bad FQTN: " + fqtn);
            return new TableRef(p[0], p[1], p[2]);
        }
    }

    /**
     * Читаем колонки локальной таблицы из information_schema.columns в нужном порядке ordinal_position.
     */
    protected List<ColumnInfo> fetchLocalColumnInfo(TableRef ref) {
        String sql = "SELECT column_name, data_type " +
                "FROM " + quoteIdent(ref.catalog) + ".information_schema.columns " +
                "WHERE table_schema = ? AND table_name = ? " +
                "ORDER BY ordinal_position";
        return localTrino.query(sql, ps -> {
            ps.setString(1, ref.schema);
            ps.setString(2, ref.table);
        }, (rs, rowNum) -> new ColumnInfo(rs.getString(1), rs.getString(2)));
    }

    /**
     * Кавычим идентификаторы на всякий случай (Iceberg/Trino понимают двойные кавычки).
     */
    protected String quoteIdent(String ident) {
        if (ident == null) return null;
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    /**
     * Рендерим литерал под тип целевой колонки в локальной таблице.
     */
    protected String sqlLiteralForType(Object v, String targetType) {
        if (v == null) return "NULL";
        if (targetType == null) targetType = "";

        String t = targetType.toLowerCase(); // integer, bigint, double, decimal(5,1), varchar, date, boolean, ...

        // BOOLEAN
        if (t.startsWith("boolean")) {
            if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
            return String.valueOf(Boolean.parseBoolean(String.valueOf(v)));
        }

        // ЦЕЛЫЕ
        if (t.startsWith("tinyint") || t.startsWith("smallint") || t.startsWith("integer") || t.startsWith("int") || t.startsWith("bigint")) {
            if (v instanceof Number) return String.valueOf(((Number) v).longValue());
            return String.valueOf(new java.math.BigDecimal(String.valueOf(v)).longValue());
        }

        // Числа с плавающей точкой (DOUBLE/REAL) — избегаем DECIMAL-литералов, кастуем явно
        if (t.startsWith("double") || t.startsWith("real")) {
            double d = (v instanceof Number) ? ((Number) v).doubleValue() : Double.parseDouble(String.valueOf(v));
            return "CAST(" + stripNaNInfinity(d) + " AS DOUBLE)";
        }

        // DECIMAL(p,s)
        if (t.startsWith("decimal")) {
            java.math.BigDecimal bd = (v instanceof java.math.BigDecimal)
                    ? (java.math.BigDecimal) v
                    : new java.math.BigDecimal(String.valueOf(v));
            return bd.toPlainString();
        }

        // DATE
        if (t.startsWith("date")) {
            String s = String.valueOf(v);
            if (v instanceof java.sql.Date) {
                s = v.toString();
            } else if (s.length() > 10) {
                s = s.substring(0, 10);
            }
            return "DATE '" + s + "'";
        }

        // TIMESTAMP
        if (t.startsWith("timestamp")) {
            String s = String.valueOf(v);
            return "TIMESTAMP '" + s.replace('T', ' ').replace("Z", "").trim() + "'";
        }

        // VARCHAR/CHAR
        if (t.startsWith("varchar") || t.startsWith("char")) {
            return "'" + String.valueOf(v).replace("'", "''") + "'";
        }

        // Фоллбек
        return "'" + String.valueOf(v).replace("'", "''") + "'";
    }

    private String stripNaNInfinity(double d) {
        if (Double.isNaN(d)) return "nan()";
        if (Double.isInfinite(d)) return d > 0 ? "infinity()" : "-infinity()";
        java.text.DecimalFormat df = new java.text.DecimalFormat("0.###############", java.text.DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(d);
    }

    public static class QueryResult {
        public String name;
        public List<Map<String, Object>> rows;

        public static QueryResult of(String name, List<Map<String, Object>> rows) {
            QueryResult r = new QueryResult();
            r.name = name;
            r.rows = rows;
            return r;
        }

        public static QueryResult placeholder(String name) {
            return of(name, List.of());
        }
    }

    public static class ComparisonReport {
        public String status;
        public int diffRows;
        public int diffCols;

        public ComparisonReport(String status, int diffRows, int diffCols) {
            this.status = status;
            this.diffRows = diffRows;
            this.diffCols = diffCols;
        }
    }

    @FunctionalInterface
    private interface StageBody {
        Object run() throws Exception;
    }

    private void stageWrap(List<ValidationModels.StageResult> stages,
                           List<ValidationModels.ValidationError> errors,
                           ValidationModels.StageName name,
                           StageBody body) {
        try {
            Object details = body.run();
            stages.add(ValidationModels.StageResult.builder()
                    .name(name)
                    .status(ValidationModels.StageStatus.OK)
                    .details(String.valueOf(details))
                    .build());
        } catch (Exception ex) {
            // неожиданный эксепшен шага — валидационная ошибка единого формата
            errors.add(ValidationModels.ValidationError.builder()
                    .stage(name)
                    .code("EXECUTION_ERROR")
                    .message(rootMessage(ex))
                    .hint(null)
                    .build());
            stages.add(ValidationModels.StageResult.builder()
                    .name(name)
                    .status(ValidationModels.StageStatus.ERROR)
                    .details(rootMessage(ex))
                    .build());
            log.warn("Stage {} failed: {}", name, ex.getMessage(), ex);
        }
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage() == null ? t.toString() : cur.getMessage();
    }

    protected List<String> applyDdlOnLocal(List<SqlFile> ddls,
                                           List<ValidationModels.ValidationError> errors,
                                           ValidationModels.StageName stage) {
        if (ddls == null || ddls.isEmpty()) return List.of();
        List<String> tableNames = new ArrayList<>();

        for (SqlFile file : ddls) {
            String raw = file.getContent();
            if (isBlank(raw)) continue;
            String sql = sanitizeDdl(raw);
            try {
                log.debug("Applying DDL: {}", left(sql, 180));
                localTrino.execute(sql);
                extractTableName(sql).ifPresent(tableNames::add);
            } catch (Exception ex) {
                errors.add(ValidationModels.ValidationError.builder()
                        .stage(stage)
                        .file(null) // если у SqlFile есть имя — подставь
                        .code(classifySqlError(ex))
                        .message(rootMessage(ex))
                        .hint(null)
                        .build());
                log.warn("DDL failed: {}", ex.getMessage(), ex);
            }
        }
        return tableNames;
    }

    protected void applyMigrationsOnLocalSafe(List<SqlFile> migrations,
                                              List<ValidationModels.ValidationError> errors) {
        if (migrations == null || migrations.isEmpty()) return;
        for (SqlFile file : migrations) {
            String sql = stripTrailingSemicolon(file.getContent());
            if (isBlank(sql)) continue;
            try {
                log.info("Applying migration: {}", left(sql, 180));
                localTrino.execute(sql);
            } catch (Exception ex) {
                errors.add(ValidationModels.ValidationError.builder()
                        .stage(ValidationModels.StageName.APPLY_MIGRATIONS)
                        .code(classifySqlError(ex))
                        .message(rootMessage(ex))
                        .build());
                log.warn("Migration failed: {}", ex.getMessage(), ex);
            }
        }
    }

    protected void loadDataForTablesFromProdSafe(String prodJdbcUrl,
                                                 List<String> localTableNames,
                                                 String sourceCatalog,
                                                 String localCatalog,
                                                 String runSuffix,
                                                 List<ValidationModels.ValidationError> errors) {
        if (localTableNames == null || localTableNames.isEmpty()) {
            return;
        }

        try (Connection prodConnection = DriverManager.getConnection(prodJdbcUrl)) {
            log.info("Connected to PROD Trino: {}", prodJdbcUrl);

            for (String localFqtn : localTableNames) {
                String prodFqtn = toProdFqtn(localFqtn, sourceCatalog, localCatalog, runSuffix);
                try {
                    TableRef localRef = TableRef.parse(localFqtn);
                    List<ColumnInfo> columns = fetchLocalColumnInfo(localRef);
                    if (columns.isEmpty()) {
                        log.warn("No columns resolved for local table {}", localFqtn);
                        continue;
                    }
                    String columnList = columns.stream().map(c -> quoteIdent(c.name)).collect(Collectors.joining(", "));
                    String selectSample = "SELECT * FROM " + prodFqtn + " LIMIT " + SAMPLE_LIMIT;

                    int inserted = 0;
                    try (PreparedStatement stmt = prodConnection.prepareStatement(selectSample);
                         ResultSet rs = stmt.executeQuery()) {

                        while (rs.next()) {
                            StringBuilder insert = new StringBuilder("INSERT INTO ")
                                    .append(localFqtn)
                                    .append(" (").append(columnList).append(") VALUES (");

                            for (int i = 0; i < columns.size(); i++) {
                                if (i > 0) insert.append(", ");
                                Object v = rs.getObject(i + 1);
                                insert.append(sqlLiteralForType(v, columns.get(i).dataType));
                            }
                            insert.append(")");
                            localTrino.execute(insert.toString());
                            inserted++;
                        }
                    }
                    log.info("Inserted {} sample rows into {}", inserted, localFqtn);
                } catch (Exception ex) {
                    errors.add(ValidationModels.ValidationError.builder()
                            .stage(ValidationModels.StageName.SAMPLE)
                            .code(classifySqlError(ex))
                            .message(String.format("Failed to load sample for %s from %s: %s", localFqtn, prodFqtn, rootMessage(ex)))
                            .build());
                    log.warn("Sampling failed for {} <- {}: {}", localFqtn, prodFqtn, ex.getMessage(), ex);
                }
            }
        } catch (Exception e) {
            errors.add(ValidationModels.ValidationError.builder()
                    .stage(ValidationModels.StageName.SAMPLE)
                    .code("CONNECTION_ERROR")
                    .message("Failed to connect to PROD: " + rootMessage(e))
                    .build());
            log.error("Data load from PROD failed: {}", e.getMessage(), e);
        }
    }

    protected List<QueryResult> runQueriesOnLocal(String tag,
                                                  List<SqlFile> queries,
                                                  ValidationModels.StageName stage,
                                                  List<ValidationModels.ValidationError> errors) {
        if (queries == null || queries.isEmpty()) return List.of();
        List<QueryResult> results = new ArrayList<>();

        for (int i = 0; i < queries.size(); i++) {
            String sql = stripTrailingSemicolon(queries.get(i).getContent());
            if (isBlank(sql)) continue;

            String name = tag + "_Q" + (i + 1);
            try {
                log.info("Running {}", name);
                List<Map<String, Object>> rows = localTrino.queryForList(sql);
                results.add(QueryResult.of(name, rows));
            } catch (Exception ex) {
                errors.add(ValidationModels.ValidationError.builder()
                        .stage(stage)
                        .file(null)
                        .code(classifySqlError(ex))
                        .message(String.format("%s failed: %s", name, rootMessage(ex)))
                        .hint("убери точку с запятой на конце")
                        .build());
                log.warn("{} failed: {}", name, ex.getMessage(), ex);
                results.add(QueryResult.placeholder(name));
            }
        }
        return results;
    }

    private String classifySqlError(Exception ex) {
        String msg = (rootMessage(ex) + "").toLowerCase();
        if (msg.contains("line") && msg.contains("mismatched input")) return "SYNTAX_ERROR";
        if (msg.contains("table") && msg.contains("does not exist")) return "MISSING_TABLE";
        if (msg.contains("schema") && msg.contains("does not exist")) return "MISSING_SCHEMA";
        if (msg.contains("column") && msg.contains("does not exist")) return "MISSING_COLUMN";
        if (msg.contains("permission") || msg.contains("denied")) return "PERMISSION_DENIED";
        if (msg.contains("connect") || msg.contains("timeout")) return "CONNECTION_ERROR";
        return "EXECUTION_ERROR";
    }

    private void ensureSchemasExist(ValidationModels.ValidationRequest normalized) {
        java.util.function.Consumer<List<SqlFile>> ensure = ddls -> {
            if (ddls == null) return;
            for (SqlFile ddl : ddls) {
                extractTableName(ddl.getContent()).ifPresent(fqtn -> {
                    String[] cs = parseCatalogAndSchema(fqtn);
                    if (cs != null) createSchemaIfMissing(cs[0], cs[1]);
                });
            }
        };
        ensure.accept(normalized.getOldDdl());
        ensure.accept(normalized.getNewDdl());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractCreatedTablesFromStage(List<ValidationModels.StageResult> stages,
                                                       ValidationModels.StageName stage) {
        // Если нужно — можно хранить список во `stageState`. Сейчас берём из applyDdlOnLocal возврата выше.
        // Для простоты — верни пустой список; либо сохраняй в stageState внутри stageWrap тела.
        Object v = stageState.get("createdOldTables");
        return v instanceof List<?> ? (List<String>) v : List.of();
    }

    public static record ValidatedArtifacts(
            List<SqlBlock> finalDdl,
            List<SqlBlock> migrations,
            List<RewrittenQuery> queries,
            ValidationModels.ValidationReport report
    ) {
    }


    private List<SqlFile> toSqlFiles(List<String> stmts) {
        if (stmts == null) return List.of();
        return stmts.stream().map(SqlFile::new).toList();
    }

    private List<SqlFile> toSqlFilesFromBlocks(List<SqlBlock> blocks) {
        if (blocks == null) return List.of();
        return blocks.stream().map(b -> new SqlFile(b.getStatement())).toList();
    }


    /**
     * Отправляем структурированные ошибки в LLM и (возможно) получаем патч.
     * Возвращаем null, если правок нет или LLM не смог помочь.
     */
    protected ValidationPatch sendValidationErrorsToLlm(
            Task task,
            ValidationModels.ValidationRequest req,
            List<ValidationModels.ValidationError> errors,
            int attempt
    ) {
        // 1) Системное сообщение
        String system = PromptTemplates.VALIDATION_AUTOFIX_SYSTEM;

        int newDdlCount = req.getNewDdl() != null ? req.getNewDdl().size() : 0;
        int migCount    = req.getMigrations() != null ? req.getMigrations().size() : 0;
        int newSqlCount = req.getNewSql() != null ? req.getNewSql().size() : 0;

        String errorsBlock = (errors == null || errors.isEmpty())
                ? "—"
                : errors.stream()
                .map(e -> "- [" + e.getStage() + "][" + (e.getCode() == null ? "UNKNOWN" : e.getCode()) + "] " + e.getMessage())
                .collect(Collectors.joining("\n"));

        // 2) Пользовательское сообщение
        String user = PromptTemplates.buildValidationAutofixUser(
                attempt,
                String.valueOf(req.getProdTrinoJdbcUrl()),
                errorsBlock,
                newDdlCount, joinSqlFiles("NEW_DDL", req.getNewDdl()),
                migCount,    joinSqlFiles("MIGRATIONS", req.getMigrations()),
                newSqlCount, joinSqlFiles("NEW_SQL", req.getNewSql())
        );

        // 3) Вызов LLM
        LlmRequest lr = LlmRequest.builder()
                .conversationId(UUID.randomUUID().toString())
                .systemMessage(system)
                .userMessage(user)
                .build();

        AutoFixOutput fix;
        try {
            fix = llmService.callAs(lr, AutoFixOutput.class);
            log.info("LLM autofix rationale: {}", fix != null ? fix.getRationale() : "<null>");
        } catch (Exception ex) {
            log.warn("LLM autofix failed: {}", ex.getMessage(), ex);
            return null;
        }
        if (fix == null) return null;

        // 4) Собираем патч
        ValidationPatch patch = new ValidationPatch();
        patch.setNewDdl(fix.getNewDdl());
        patch.setMigrations(fix.getMigrations());
        patch.setNewSql(fix.getNewSql());
        return patch;
    }

    /**
     * Логируем и, при желании, можно дернуть отдельный промпт по «общему» исключению.
     * Сейчас — просто лог.
     */
    protected void sendValidationErrorsToLlm(
            Task task,
            ValidationModels.ValidationRequest req,
            Exception e,
            int attempt
    ) {
        log.warn("LLM (exception path), attempt={}, taskId={}, error={}",
                attempt, task.getId(), e.getMessage(), e);
        // Можно при желании вызвать тот же промпт, что и выше, но на основе e.getMessage()
    }

    /**
     * Тестовый хук: "отправляем" ошибки в LLM и (пока что) ничего не меняем.
     * Здесь можно будет интегрировать LlmService и вернуть обновлённый request.
     */
    private ValidationModels.ValidationRequest sendValidationErrorsToLlmAndMaybePatch(
            ValidationModels.ValidationRequest req, Exception error, int attempt
    ) {
        String system = PromptTemplates.VALIDATION_AUTOFIX_SYSTEM;

        int newDdlCount = req.getNewDdl() != null ? req.getNewDdl().size() : 0;
        int migCount    = req.getMigrations() != null ? req.getMigrations().size() : 0;
        int newSqlCount = req.getNewSql() != null ? req.getNewSql().size() : 0;

        String user = PromptTemplates.buildValidationExceptionUser(
                attempt,
                String.valueOf(error.getMessage()),
                String.valueOf(req.getProdTrinoJdbcUrl()),
                newDdlCount, joinSqlFiles("NEW_DDL", req.getNewDdl()),
                migCount,    joinSqlFiles("MIGRATIONS", req.getMigrations()),
                newSqlCount, joinSqlFiles("NEW_SQL", req.getNewSql())
        );

        LlmRequest lr = LlmRequest.builder()
                .conversationId(UUID.randomUUID().toString())
                .systemMessage(system)
                .userMessage(user)
                .build();

        AutoFixOutput fix;
        try {
            fix = llmService.callAs(lr, AutoFixOutput.class);
            log.info("LLM autofix rationale: {}", fix != null ? fix.getRationale() : "<null>");
        } catch (Exception ex) {
            log.warn("LLM autofix failed: {}", ex.getMessage(), ex);
            return req;
        }
        if (fix == null) return req;

        return ValidationModels.ValidationRequest.builder()
                .prodTrinoJdbcUrl(req.getProdTrinoJdbcUrl())
                .oldDdl(req.getOldDdl())
                .oldSql(req.getOldSql())
                .newDdl(fix.getNewDdl() != null ? toSqlFiles(fix.getNewDdl()) : req.getNewDdl())
                .migrations(fix.getMigrations() != null ? toSqlFiles(fix.getMigrations()) : req.getMigrations())
                .newSql(fix.getNewSql() != null ? toSqlFiles(fix.getNewSql()) : req.getNewSql())
                .sampling(req.getSampling())
                .catalog(req.getCatalog())
                .comparison(req.getComparison())
                .build();
    }

    private static String joinSqlFiles(String tag, List<SqlFile> files) {
        if (files == null || files.isEmpty()) return "-- " + tag + " [пусто]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            SqlFile f = files.get(i);
            sb.append("-- ").append(tag).append("[").append(i + 1).append("]\n");
            sb.append(f != null && f.getContent() != null ? f.getContent() : "-- <null>").append("\n\n");
        }
        return sb.toString();
    }

    /** Откуда берём JDBC URL PROD Trino. При необходимости замени на конфиг. */
    protected String resolveProdJdbcUrl(Task task) {
        try {
            String url = task.getInput().getPayload().getUrl();
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Payload URL is empty");
            }
            return url;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve PROD JDBC URL from task payload", e);
        }
    }

    private List<SqlBlock> toSqlBlocks(List<String> stmts) {
        if (stmts == null) return List.of();
        return stmts.stream()
                .map(s -> SqlBlock.builder().statement(s).build())
                .toList();
    }

    private static String stripTrailingSemicolon(String sql) {
        if (sql == null) return null;
        return sql.replaceFirst(";\\s*$", "");
    }
}