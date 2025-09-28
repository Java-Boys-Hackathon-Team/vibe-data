package ru.javaboys.vibe_data.agent;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.javaboys.vibe_data.agent.tools.TrinoExplainTools;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.jsonb.DdlStatement;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;
import ru.javaboys.vibe_data.llm.LlmRequest;
import ru.javaboys.vibe_data.llm.LlmService;
import ru.javaboys.vibe_data.repository.TaskResultRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryOptimizerAgent {

    private final LlmService llmService;
    private final TrinoExplainTools trinoExplainTools;
    private final TaskResultRepository taskResultRepository;
    private final PlatformTransactionManager transactionManager;

    public TaskResult optimize(Task task) {
        var payload = task.getInput().getPayload();

        log.info("Старт оптимизации задачи id={}: входные данные — DDL={}, запросов={}",
                task.getId(),
                payload.getDdl() != null ? payload.getDdl().size() : 0,
                payload.getQueries() != null ? payload.getQueries().size() : 0);

        // 1. Системный промпт
        String conversationId = task.getId().toString();
        String system = PromptTemplates.SYSTEM_ROLE;

        Map<String, Object> sysVars = Map.of(
                "rules", PromptTemplates.RULES,
                "catalogSchemaRule", PromptTemplates.CATALOG_SCHEMA_RULE
        );

        // 2. исходный DDL как контекст
        String originalDdlJoined = payload.getDdl().stream()
                .map(DdlStatement::getStatement)
                .collect(Collectors.joining("\n\n"));

        // 3. сортировка запросов по весу: runquantity * executiontime (если executiontime==0, считаем 1)
        List<QueryInput> sorted = new ArrayList<>(payload.getQueries());
        sorted.sort((a, b) -> {
            long wa = (long) a.getRunquantity() * Math.max(1L, a.getExecutiontime());
            long wb = (long) b.getRunquantity() * Math.max(1L, b.getExecutiontime());
            return Long.compare(wb, wa);
        });
        log.info("Запросов к оптимизации: {}. Запускаем итеративный цикл.", sorted.size());

        // 4. копим изменения DDL по шагам
        List<SqlBlock> accumulatedDdl = new ArrayList<>();
        // Карта optimizedQuery by id
        Map<String, RewrittenQuery> optimizedQueries = new LinkedHashMap<>();

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
                        payload.getQueries().stream()
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
                    payload.getQueries().stream()
                            .map(qq -> optimizedQueries.getOrDefault(qq.getQueryid(),
                                    RewrittenQuery.builder().queryid(qq.getQueryid()).query(qq.getQuery()).build()))
                            .toList());
            log.info("Итерация {}: промежуточный прогресс сохранён в TaskResult id={}", idx, saved.getId());
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

        List<RewrittenQuery> finalQueries = payload.getQueries().stream()
                .map(q -> optimizedQueries.getOrDefault(q.getQueryid(),
                        // fallback: если по какой-то причине нет оптимизации — вернуть исходный
                        RewrittenQuery.builder().queryid(q.getQueryid()).query(q.getQuery()).build()))
                .toList();

        log.info("Формирование результата завершено: финальный DDL={}, миграций={}, переписанных запросов={}",
                finalDdl != null ? finalDdl.size() : 0,
                migrations != null ? migrations.size() : 0,
                finalQueries != null ? finalQueries.size() : 0);

        // Финальное сохранение результата в БД с немедленной фиксацией
        TaskResult savedFinal = persistToDb(task, finalDdl, migrations, finalQueries);
        return savedFinal;
    }

    private PerQueryOptimizationOutput runQueryOptimizationStep(
            String conversationId,
            String system,
            Map<String, Object> sysVars,
            String originalDdl,
            List<SqlBlock> accumulatedDdl,
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

        // Tools: отдаём набор инструментов EXPLAIN/ANALYZE
        List<Object> tools = List.of(trinoExplainTools);

        return llmService.callAs(
                LlmRequest.builder()
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
            List<SqlBlock> accumulatedDdl
    ) {
        String userTemplate = PromptTemplates.MIGRATION_SYNTHESIS_PROMPT;

        Map<String, Object> userVars = Map.of(
                "original_ddl", originalDdl,
                "optimized_ddl", accumulatedDdl.stream()
                        .map(SqlBlock::getStatement).collect(Collectors.joining("\n\n"))
        );

        List<Object> tools = List.of(trinoExplainTools);

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
}
