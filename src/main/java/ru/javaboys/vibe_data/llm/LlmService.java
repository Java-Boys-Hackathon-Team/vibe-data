package ru.javaboys.vibe_data.llm;

import java.util.List;
import java.util.Map;

public interface LlmService {
    String llmCall(String conversationId,
                   String systemMessage,
                   Map<String, Object> systemVariables,
                   String userMessage,
                   Map<String, Object> userVariables,
                   List<Object> tools);

    <T> T llmCallToBean(String conversationId,
                            String systemMessage,
                            Map<String, Object> systemVariables,
                            String userMessage,
                            Map<String, Object> userVariables,
                            List<Object> tools,
                            Class<T> classType);
}
