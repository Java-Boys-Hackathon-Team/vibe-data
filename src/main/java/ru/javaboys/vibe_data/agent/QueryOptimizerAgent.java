package ru.javaboys.vibe_data.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainTools;
import ru.javaboys.vibe_data.agent.tools.TrinoReadOnlyQueryTools;
import ru.javaboys.vibe_data.domain.Optimization;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.jsonb.DdlStatement;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;
import ru.javaboys.vibe_data.llm.LlmRequest;
import ru.javaboys.vibe_data.llm.LlmService;
import ru.javaboys.vibe_data.monitoring.MethodStatsRepository;
import ru.javaboys.vibe_data.repository.OptimizationRepository;
import ru.javaboys.vibe_data.repository.TaskResultRepository;
import ru.javaboys.vibe_data.validator.ValidationSwitcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryOptimizerAgent {

    private final LlmService llmService;
    private final TrinoExplainTools trinoExplainTools;
    private final TrinoReadOnlyQueryTools trinoReadOnlyQueryTools;
    private final TaskResultRepository taskResultRepository;
    private final PlatformTransactionManager transactionManager;
    private final OptimizationRepository optimizationRepository;
    private final ValidationSwitcher validationSwitcher;

    // --- Time-budget configuration ---
    private final MethodStatsRepository methodStatsRepository;

    @Value("${processing.max-total-duration-ms}")
    private long maxTotalDurationMs;

    @Value("${validation.max-attempts}")
    private int validationMaxAttempts;

    @Value("${processing.default-llm-avg-ms}")
    private long defaultLlmAvgMs;

    public TaskResult optimize(Task task) {
        var payload = task.getInput().getPayload();

        long startedAtNs = System.nanoTime();

        log.info("Старт оптимизации задачи id={}: входные данные — DDL={}, запросов={}, бюджет времени={} мс",
                task.getId(),
                payload.getDdl() != null ? payload.getDdl().size() : 0,
                payload.getQueries() != null ? payload.getQueries().size() : 0,
                maxTotalDurationMs);

        // 1. Системный промпт
        String conversationId = task.getId().toString();
        String system;
        Map<String, Object> sysVars;

        List<Optimization> optimizations = optimizationRepository.findAllByActiveIsTrue();
        if (optimizations.isEmpty()) {
            system = PromptTemplates.SYSTEM_ROLE;
            sysVars = Map.of(
                    "rules", PromptTemplates.RULES,
                    "catalogSchemaRule", PromptTemplates.CATALOG_SCHEMA_RULE
            );
        } else {
            String optimizationsLine = optimizations.stream()
                    .map(Optimization::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            system = PromptTemplates.SYSTEM_ROLE_WIITH_OPTIMIZATIONS;
            sysVars = Map.of(
                    "optimizations", optimizationsLine,
                    "rules", PromptTemplates.RULES,
                    "catalogSchemaRule", PromptTemplates.CATALOG_SCHEMA_RULE
            );
        }

        // 2. исходный DDL как контекст
        String originalDdlJoined = payload.getDdl().stream()
                .map(DdlStatement::getStatement)
                .collect(Collectors.joining("\n\n"));

        // 3. удаление дублей по полю QueryInput.query + сортировка по весу
        List<QueryInput> inputQueries = payload.getQueries();
        List<QueryInput> unique = dedupeQueriesByText(inputQueries);
        if (inputQueries != null && unique != null && unique.size() < inputQueries.size()) {
            log.info("Обнаружены дубли запросов: {} → {} (критерий уникальности — поле query)",
                    inputQueries.size(), unique.size());
        }
        List<QueryInput> sorted = new ArrayList<>(unique);
        sorted.sort((a, b) -> {
            long wa = weightOf(a);
            long wb = weightOf(b);
            return Long.compare(wb, wa);
        });
        log.info("Запросов к оптимизации: {}. Запускаем итеративный цикл.", sorted.size());

        // 4. копим изменения DDL по шагам
        Set<SqlBlock> accumulatedDdl = new LinkedHashSet<>();
        // Карта optimizedQuery by id
        Map<String, RewrittenQuery> optimizedQueries = new LinkedHashMap<>();

        // --- Вспомогательные значения бюджета ---
        double avgLlmMsCached;
        // кол-во зарезервированных обязательных LLM-запросов
        int reserveCallsForMandatory = 1 + Math.max(0, validationMaxAttempts);

        // 5. Итеративная оптимизация
        for (int i = 0; i < sorted.size(); i++) {
            QueryInput q = sorted.get(i);
            int idx = i + 1;
            int total = sorted.size();
            int remaining = total - idx;
            log.info("Итерация {} из {} (осталось {}): оптимизация запроса id={}, runquantity={}, executiontime={}",
                    idx, total, remaining, q.getQueryid(), q.getRunquantity(), Math.max(1, q.getExecutiontime()));

            PerQueryOptimizationOutput out;
            try {
                out = runQueryOptimizationStep(
                        task.getLlmModel(),
                        task.getTemperature(),
                        conversationId,
                        system,
                        sysVars,
                        originalDdlJoined,
                        accumulatedDdl,
                        q
                );
            } catch (Exception e) {
                log.error("Ошибка оптимизации запроса id={} на итерации {}: {}", q.getQueryid(), idx, e.getMessage(), e);
                // Перед выбросом ошибки фиксируем накопленные результаты
                persistToDb(task,
                        new ArrayList<>(accumulatedDdl),
                        List.of(),
                        unique.stream()
                                .map(qq -> optimizedQueries.getOrDefault(qq.getQueryid(),
                                        RewrittenQuery.builder().queryid(qq.getQueryid()).query(qq.getQuery()).build()))
                                .toList());
                throw e;
            }

            // Сохраняем перезаписанный запрос
            optimizedQueries.put(q.getQueryid(), RewrittenQuery.builder()
                    .queryid(q.getQueryid())
                    .query(out.rewrittenQuery())
                    .build());

            int ddlChangesCount = out.ddlChanges() != null ? out.ddlChanges().size() : 0;
            log.info("Итерация {}: оптимизация запроса id={} завершена, изменений DDL: {}", idx, q.getQueryid(), ddlChangesCount);

            // Накапливаем DDL изменения, если пришли
            if (out.ddlChanges() != null && !out.ddlChanges().isEmpty()) {
                for (String stmt : out.ddlChanges()) {
                    accumulatedDdl.add(SqlBlock.builder().statement(stmt).build());
                }
            }

            // Промежуточное сохранение прогресса (DDL и Queries), миграции пока пустые
            TaskResult saved = persistToDb(task,
                    new ArrayList<>(accumulatedDdl),
                    List.of(),
                    unique.stream()
                            .map(qq -> optimizedQueries.getOrDefault(qq.getQueryid(),
                                    RewrittenQuery.builder().queryid(qq.getQueryid()).query(qq.getQuery()).build()))
                            .toList());
            log.info("Итерация {}: промежуточный прогресс сохранён в TaskResult id={}", idx, saved.getId());

            // --- Проверка бюджета времени: хватит ли на обязательные шаги? ---
            // Обновляем среднюю длительность LLM, чтобы подстраиваться по ходу выполнения
            avgLlmMsCached = resolveAvgLlmMs();
            // elapsedMs - сколько уже времени прошло
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNs);
            // remainingMs - оставшееся время на всё
            long remainingMs = maxTotalDurationMs - elapsedMs;
            // резерв времени на обязательные шаги
            long reserveMs = (long) Math.ceil(reserveCallsForMandatory * avgLlmMsCached);
            // оставшееся время для оптимизаций
            long availableForOptimizationMs = remainingMs - reserveMs;

            if (availableForOptimizationMs < Math.ceil(avgLlmMsCached)) {
                log.info("Остановка итеративной оптимизации на шаге {}: оставшееся время={} мс, резерв на обязательные шаги={} мс ({} LLM), средний LLM={} мс.",
                        idx, remainingMs, reserveMs, reserveCallsForMandatory, Math.round(avgLlmMsCached));
                break; // выходим из цикла, переходим к генерации миграций и валидации
            } else {
                long mayDo = (long) Math.floor(availableForOptimizationMs / Math.max(1.0, avgLlmMsCached));
                log.info("После итерации {} остаётся ~{} мс для оптимизации (без резерва). Этого хватит примерно на {} LLM-запрос(ов).",
                        idx, availableForOptimizationMs, mayDo);
            }
        }

        // 6. Генерация миграций (финальный шаг)
        log.info("Начинаем генерацию миграций и финального DDL. Накоплено изменений DDL: {}", accumulatedDdl.size());
        FinalMigrationOutput migrationsOut;
        try {
            migrationsOut = runMigrationSynthesis(
                    conversationId,
                    system,
                    sysVars,
                    originalDdlJoined,
                    accumulatedDdl
            );
        } catch (Exception e) {
            log.error("Ошибка при генерации миграций: {}", e.getMessage(), e);
            throw e;
        }

        // 7. Сбор финального результата
        List<SqlBlock> finalDdl = normalizeDdlOrder(migrationsOut.newDdl());
        List<SqlBlock> migrations = toSqlBlocks(migrationsOut.migrations());

        List<RewrittenQuery> finalQueries = unique.stream()
                .map(q -> optimizedQueries.getOrDefault(q.getQueryid(),
                        RewrittenQuery.builder().queryid(q.getQueryid()).query(q.getQuery()).build()))
                .toList();

        log.info("Формирование результата завершено: финальный DDL={}, миграций={}, переписанных запросов={}",
                finalDdl != null ? finalDdl.size() : 0,
                migrations != null ? migrations.size() : 0,
                finalQueries != null ? finalQueries.size() : 0);

        // 8. Валидация итогового результата
        var validated = validationSwitcher.validate(task, finalDdl, migrations, finalQueries);

        finalDdl = validated.finalDdl();
        migrations = validated.migrations();
        finalQueries = validated.queries();

        // Финальное сохранение результата в БД с немедленной фиксацией
        TaskResult savedFinal = persistToDb(task, finalDdl, migrations, finalQueries);
        return savedFinal;
    }

    private PerQueryOptimizationOutput runQueryOptimizationStep(
            String llmModel,
            Double temperature,
            String conversationId,
            String system,
            Map<String, Object> sysVars,
            String originalDdl,
            Set<SqlBlock> accumulatedDdl,
            QueryInput q
    ) {
        String userTemplate = PromptTemplates.QUERY_OPTIMIZATION_PROMPT;

        Map<String, Object> userVars = new HashMap<>();
        userVars.put("original_ddl", originalDdl);
        userVars.put("accumulated_ddl", accumulatedDdl.stream()
                .map(SqlBlock::getStatement).collect(Collectors.joining("\n\n")));
        userVars.put("runquantity", q.getRunquantity());
        userVars.put("executiontime", Math.max(1, q.getExecutiontime()));
        userVars.put("queryid", q.getQueryid());
        userVars.put("query_sql", q.getQuery());

        // Tools: отдаём набор инструментов EXPLAIN/ANALYZE + чтение read-only
        List<Object> tools = List.of(trinoExplainTools, trinoReadOnlyQueryTools);

        return llmService.callAs(
                LlmRequest.builder()
                        .llmModel(llmModel)
                        .temperature(temperature)
                        .conversationId(conversationId)
                        .systemMessage(system)
                        .systemVariables(sysVars)
                        .userMessage(userTemplate)
                        .userVariables(userVars)
                        .tools(tools)
                        .build(),
                PerQueryOptimizationOutput.class
        );
    }

    private FinalMigrationOutput runMigrationSynthesis(
            String conversationId,
            String system,
            Map<String, Object> sysVars,
            String originalDdl,
            Set<SqlBlock> accumulatedDdl
    ) {
        String userTemplate = PromptTemplates.MIGRATION_SYNTHESIS_PROMPT;

        Map<String, Object> userVars = Map.of(
                "original_ddl", originalDdl,
                "optimized_ddl", accumulatedDdl.stream()
                        .map(SqlBlock::getStatement).collect(Collectors.joining("\n\n"))
        );

        List<Object> tools = List.of(trinoExplainTools, trinoReadOnlyQueryTools);

        return llmService.callAs(
                LlmRequest.builder()
                        .conversationId(conversationId)
                        .systemMessage(system)
                        .systemVariables(sysVars)
                        .userMessage(userTemplate)
                        .userVariables(userVars)
                        .tools(tools)
                        .build(),
                FinalMigrationOutput.class
        );
    }

    // Сохранение текущего прогресса в отдельной новой транзакции (мгновенная фиксация)
    private TaskResult persistToDb(Task task,
                                   List<SqlBlock> ddl,
                                   List<SqlBlock> migrations,
                                   List<RewrittenQuery> queries) {
        TransactionTemplate tmpl = new TransactionTemplate(transactionManager);
        tmpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tmpl.execute(status -> {
            var existingOpt = taskResultRepository.findByTaskId(task.getId());
            TaskResult entity = existingOpt.orElseGet(() -> TaskResult.builder()
                    .task(task)
                    .ddl(new ArrayList<>())
                    .migrations(new ArrayList<>())
                    .queries(new ArrayList<>())
                    .build());

            // поля не должны быть null
            entity.setTask(task);
            entity.setDdl(ddl != null ? ddl : List.of());
            entity.setMigrations(migrations != null ? migrations : List.of());
            entity.setQueries(queries != null ? queries : List.of());

            TaskResult saved = taskResultRepository.saveAndFlush(entity);
            return saved;
        });
    }

    private double resolveAvgLlmMs() {
        try {
            var asOpt = methodStatsRepository.findByKey("llm.call.as");
            if (asOpt.isPresent() && asOpt.get().getAvgTimeMs() != null && asOpt.get().getAvgTimeMs() > 0) {
                return asOpt.get().getAvgTimeMs();
            }
            var callOpt = methodStatsRepository.findByKey("llm.call");
            if (callOpt.isPresent() && callOpt.get().getAvgTimeMs() != null && callOpt.get().getAvgTimeMs() > 0) {
                return callOpt.get().getAvgTimeMs();
            }
        } catch (Exception ignored) {
        }
        return Math.max(1, (int) defaultLlmAvgMs);
    }

    private List<SqlBlock> normalizeDdlOrder(List<String> ddlStatements) {
        // Требование ТЗ: первой командой должен идти CREATE SCHEMA <catalog>.<schema>
        // Предполагаем, что LLM сгенерировал корректно; на всякий случай
        // двигаем все CREATE SCHEMA в начало, сохраняя относительный порядок.
        if (ddlStatements == null) return List.of();
        List<String> createSchema = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String s : ddlStatements) {
            if (s.trim().toUpperCase().startsWith("CREATE SCHEMA ")) {
                createSchema.add(s);
            } else {
                rest.add(s);
            }
        }
        List<String> ordered = new ArrayList<>();
        ordered.addAll(createSchema);
        ordered.addAll(rest);
        return toSqlBlocks(ordered);
    }

    private List<SqlBlock> toSqlBlocks(List<String> stmts) {
        if (stmts == null) return List.of();
        return stmts.stream()
                .map(s -> SqlBlock.builder().statement(s).build())
                .collect(Collectors.toList());
    }

    // Вес запроса для сортировки и выбора лучшего дубля
    private long weightOf(QueryInput q) {
        if (q == null) return 0L;
        return (long) q.getRunquantity() * Math.max(1L, q.getExecutiontime());
    }

    // Удаление дублей по точному совпадению текста запроса (QueryInput.query)
    // При наличии дублей сохраняем тот, у которого максимальный вес
    private List<QueryInput> dedupeQueriesByText(List<QueryInput> queries) {
        if (queries == null || queries.isEmpty()) return List.of();
        Map<String, QueryInput> bestByQuery = new LinkedHashMap<>();
        for (QueryInput q : queries) {
            if (q == null) continue;
            String key = q.getQuery();
            if (key == null) continue;
            QueryInput current = bestByQuery.get(key);
            if (current == null || weightOf(q) > weightOf(current)) {
                bestByQuery.put(key, q);
            }
        }
        return new ArrayList<>(bestByQuery.values());
    }
}
