package ru.javaboys.vibe_data.llm;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmRequest {
    private String llmModel;
    private Double temperature;
    // id, по которому сохраняется контекст общений с LLM, необязательный
    private String conversationId;

    // системный промпт, хотя бы один раз должен быть передан, необязательный
    private String systemMessage;

    // параметры шаблона системного промпта, если null, то считается, что в системном промпте нет параметров, необязательный
    private Map<String, Object> systemVariables;

    // пользовательский промпт, обязателен
    private String userMessage;

    // параметры шаблона пользовательского промпта, если null, то считается, что в пользовательском промпте нет параметров, необязательный
    private Map<String, Object> userVariables;

    // список инструментов, которые может использовать LLM, необязательный
    private List<Object> tools;
}
