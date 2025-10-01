package ru.javaboys.vibe_data.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.javaboys.vibe_data.dto.TrinoResponse;
import ru.javaboys.vibe_data.service.TrinoDbService;

/**
 * Набор инструментов, доступных LLM через Tool Calling (Spring AI Function Calling).
 * Позволяет модели самостоятельно запрашивать планы выполнения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrinoExplainTools {

    private final TrinoDbService trinoDbService;

    @Tool(description = "Получить план EXPLAIN LOGICAL для указанного SQL.")
    public TrinoResponse explainLogical(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        return trinoDbService.explain(sql, TrinoExplainType.LOGICAL);
    }

    @Tool(description = "Получить план EXPLAIN DISTRIBUTED для указанного SQL.")
    public TrinoResponse explainDistributed(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        return trinoDbService.explain(sql, TrinoExplainType.DISTRIBUTED);
    }

    @Tool(description = "Получить план EXPLAIN IO для указанного SQL; включает сводку ввода-вывода.")
    public TrinoResponse explainIo(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        return trinoDbService.explain(sql, TrinoExplainType.IO);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE для указанного SQL. ВНИМАНИЕ: запрос будет выполнен.")
    public TrinoResponse explainAnalyze(@ToolParam(description = "SQL-запрос, для которого нужно получить метрики исполнения") String sql) {
        return trinoDbService.explain(sql, TrinoExplainType.ANALYZE);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE VERBOSE для указанного SQL. ВНИМАНИЕ: запрос будет выполнен.")
    public TrinoResponse explainAnalyzeVerbose(@ToolParam(description = "SQL-запрос, для которого нужно получить подробные метрики исполнения") String sql) {
        return trinoDbService.explain(sql, TrinoExplainType.ANALYZE_VERBOSE);
    }
}
