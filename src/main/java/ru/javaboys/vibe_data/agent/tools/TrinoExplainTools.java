package ru.javaboys.vibe_data.agent.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Набор инструментов, доступных LLM через Tool Calling (Spring AI Function Calling).
 * Позволяет модели самостоятельно запрашивать планы выполнения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrinoExplainTools {

    private final TrinoMetricToolService trinoMetricToolService;

    @Tool(description = "Получить план EXPLAIN LOGICAL для указанного SQL.")
    public String explainLogical(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        log.info("Вызван инструмент explainLogical с SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.LOGICAL);
    }

    @Tool(description = "Получить план EXPLAIN DISTRIBUTED для указанного SQL.")
    public String explainDistributed(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        log.info("Вызван инструмент explainDistributed с SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.DISTRIBUTED);
    }

    @Tool(description = "Получить план EXPLAIN IO для указанного SQL; включает сводку ввода-вывода.")
    public String explainIo(@ToolParam(description = "SQL-запрос, для которого нужно получить план") String sql) {
        log.info("Вызван инструмент explainIo с SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.IO);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE для указанного SQL. ВНИМАНИЕ: запрос будет выполнен.")
    public String explainAnalyze(@ToolParam(description = "SQL-запрос, для которого нужно получить метрики исполнения") String sql) {
        log.info("Вызван инструмент explainAnalyze с SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.ANALYZE);
    }

    @Tool(description = "Выполнить EXPLAIN ANALYZE VERBOSE для указанного SQL. ВНИМАНИЕ: запрос будет выполнен.")
    public String explainAnalyzeVerbose(@ToolParam(description = "SQL-запрос, для которого нужно получить подробные метрики исполнения") String sql) {
        log.info("Вызван инструмент explainAnalyzeVerbose с SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.ANALYZE_VERBOSE);
    }
}
