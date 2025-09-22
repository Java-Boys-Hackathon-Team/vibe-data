package ru.javaboys.vibe_data.llm;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LlmRequest {
    private String conversationId;
    private String systemMessage;
    private Map<String, Object> systemVariables;
    private String userMessage;
    private Map<String, Object> userVariables;
    private List<Object> tools;
}
