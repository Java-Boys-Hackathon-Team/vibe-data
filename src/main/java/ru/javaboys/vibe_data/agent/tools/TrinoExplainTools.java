package ru.javaboys.vibe_data.agent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import ru.javaboys.vibe_data.dto.TrinoResponse;
import ru.javaboys.vibe_data.service.TrinoDbService;

import java.util.UUID;

/**
 * Набор инструментов, доступных LLM через Tool Calling (Spring AI Function Calling).
 * Позволяет модели самостоятельно запрашивать планы выполнения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrinoExplainTools {

    private final TrinoDbService trinoDbService;

    @Tool(description = "Получить план EXPLAIN LOGICAL для запроса по queryid в рамках задачи taskId.")
    public TrinoResponse explainLogical(
            @ToolParam(description = "ID задачи оптимизации") UUID taskId,
            @ToolParam(description = "ID запроса для оптимизации") String queryid
    ) {
        return trinoDbService.explain(taskId, queryid, TrinoExplainType.LOGICAL);
    }

    @Tool(description = "Получить план EXPLAIN DISTRIBUTED для запроса по queryid в рамках задачи taskId.")
    public TrinoResponse explainDistributed(
            @ToolParam(description = "ID задачи оптимизации") UUID taskId,
            @ToolParam(description = "ID запроса для оптимизации") String queryid
    ) {
        return trinoDbService.explain(taskId, queryid, TrinoExplainType.DISTRIBUTED);
    }

    @Tool(description = "Получить план EXPLAIN IO для запроса по queryid в рамках задачи taskId; включает сводку ввода-вывода.")
    public TrinoResponse explainIo(
            @ToolParam(description = "ID задачи оптимизации") UUID taskId,
            @ToolParam(description = "ID запроса для оптимизации") String queryid
    ) {
        return trinoDbService.explain(taskId, queryid, TrinoExplainType.IO);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE для запроса по queryid в рамках задачи taskId. ВНИМАНИЕ: запрос будет выполнен.")
    public TrinoResponse explainAnalyze(
            @ToolParam(description = "ID задачи оптимизации") UUID taskId,
            @ToolParam(description = "ID запроса для оптимизации") String queryid
    ) {
        return trinoDbService.explain(taskId, queryid, TrinoExplainType.ANALYZE);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE VERBOSE для запроса по queryid в рамках задачи taskId. ВНИМАНИЕ: запрос будет выполнен.")
    public TrinoResponse explainAnalyzeVerbose(
            @ToolParam(description = "ID задачи оптимизации") UUID taskId,
            @ToolParam(description = "ID запроса для оптимизации") String queryid
    ) {
        return trinoDbService.explain(taskId, queryid, TrinoExplainType.ANALYZE_VERBOSE);
    }
}
