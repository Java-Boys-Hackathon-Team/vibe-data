package ru.javaboys.vibe_data.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;
import ru.javaboys.vibe_data.service.TrinoMetricToolService;

@RequiredArgsConstructor
public class TrinoMetricTool {
    private final TrinoMetricToolService service;

//    @Tool
    public String requestAnalyzeInJson(
            @ToolParam(description = "Sql script") String sql,
            @ToolParam(description = "Explain type") TrinoExplainType type
    ) {
        return service.requestExplainInJson(sql, type);
    }

}
