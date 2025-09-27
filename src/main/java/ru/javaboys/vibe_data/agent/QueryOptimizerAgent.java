package ru.javaboys.vibe_data.agent;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.jsonb.DdlStatement;
import ru.javaboys.vibe_data.domain.jsonb.QueryInput;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;
import ru.javaboys.vibe_data.llm.LlmRequest;
import ru.javaboys.vibe_data.llm.LlmService;
import ru.javaboys.vibe_data.tools.TrinoPlanTools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Основной оркестратор шага оптимизации:
 *  - готовит системный промпт
 *  - сортирует запросы по весу (runquantity * executiontime)
 *  - итеративно оптимизирует каждый запрос, сохраняя queryid
 *  - агрегирует DDL-изменения
 *  - генерирует миграции
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryOptimizerAgent {

    private final LlmService llmService;
    private final TrinoPlanTools trinoPlanTools;

    public TaskResult optimize(Task task) {
        var payload = task.getInput().getPayload();

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

        // 4. копим изменения DDL по шагам
        List<SqlBlock> accumulatedDdl = new ArrayList<>();
        // Карта optimizedQuery by id
        Map<String, RewrittenQuery> optimizedQueries = new LinkedHashMap<>();

        // 5. Итеративная оптимизация
        for (int i = 0; i < sorted.size(); i++) {
            QueryInput q = sorted.get(i);
            PerQueryOptimizationOutput out = runQueryOptimizationStep(
                    conversationId,
                    system,
                    sysVars,
                    originalDdlJoined,
                    accumulatedDdl,
                    q
            );

            // Сохраняем перезаписанный запрос
            optimizedQueries.put(q.getQueryid(), RewrittenQuery.builder()
                    .queryid(q.getQueryid())
                    .query(out.rewrittenQuery())
                    .build());

            // Накапливаем DDL изменения, если пришли
            if (out.ddlChanges() != null && !out.ddlChanges().isEmpty()) {
                for (String stmt : out.ddlChanges()) {
                    accumulatedDdl.add(SqlBlock.builder().statement(stmt).build());
                }
            }
        }

        // 6. Генерация миграций (финальный шаг)
        FinalMigrationOutput migrationsOut = runMigrationSynthesis(
                conversationId,
                system,
                sysVars,
                originalDdlJoined,
                accumulatedDdl
        );

        // 7. Сбор финального результата
        List<SqlBlock> finalDdl = normalizeDdlOrder(migrationsOut.newDdl());
        List<SqlBlock> migrations = toSqlBlocks(migrationsOut.migrations());

        List<RewrittenQuery> finalQueries = payload.getQueries().stream()
                .map(q -> optimizedQueries.getOrDefault(q.getQueryid(),
                        // fallback: если по какой-то причине нет оптимизации — вернуть исходный
                        RewrittenQuery.builder().queryid(q.getQueryid()).query(q.getQuery()).build()))
                .toList();

        return TaskResult.builder()
                .task(task)
                .ddl(finalDdl)
                .migrations(migrations)
                .queries(finalQueries)
                .build();
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
        List<Object> tools = List.of(trinoPlanTools);

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

        List<Object> tools = List.of(trinoPlanTools);

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
