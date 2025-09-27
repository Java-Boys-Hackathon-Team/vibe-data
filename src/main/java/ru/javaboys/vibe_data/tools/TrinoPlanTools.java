package ru.javaboys.vibe_data.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import ru.javaboys.vibe_data.llm.TrinoExplainType;
import ru.javaboys.vibe_data.service.TrinoMetricToolService;

/**
 * Набор инструментов, доступных LLM через Tool Calling (Spring AI Function Calling).
 * Позволяет модели самостоятельно запрашивать планы выполнения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrinoPlanTools {

    private final TrinoMetricToolService trinoMetricToolService;

    @Tool(description = "Get Trino EXPLAIN LOGICAL plan in JSON (FORMAT JSON) for given SQL.")
    public String explainLogicalJson(String sql) {
        log.info("Tool explainLogicalJson called with SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.LOGICAL);
        // Возвращаем JSON-план
    }

    @Tool(description = "Get Trino EXPLAIN DISTRIBUTED plan in JSON (FORMAT JSON) for given SQL.")
    public String explainDistributedJson(String sql) {
        log.info("Tool explainDistributedJson called with SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.DISTRIBUTED);
    }

    @Tool(description = "Get Trino EXPLAIN IO plan in JSON (FORMAT JSON) for given SQL; shows I/O summary.")
    public String explainIoJson(String sql) {
        log.info("Tool explainIoJson called with SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.IO);
    }

    @Tool(description = "Run EXPLAIN ANALYZE (no JSON) to obtain runtime metrics for given SQL. WARNING: executes the query.")
    public String explainAnalyze(String sql) {
        log.info("Tool explainAnalyze called with SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.ANALYZE);
    }

    @Tool(description = "Run EXPLAIN ANALYZE VERBOSE for detailed runtime metrics (no JSON). WARNING: executes the query.")
    public String explainAnalyzeVerbose(String sql) {
        log.info("Tool explainAnalyzeVerbose called with SQL: {}", sql);
        return trinoMetricToolService.requestExplainInJson(sql, TrinoExplainType.ANALYZE_VERBOSE);
    }
}
