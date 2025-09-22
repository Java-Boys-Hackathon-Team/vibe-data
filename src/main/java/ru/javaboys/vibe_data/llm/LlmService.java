package ru.javaboys.vibe_data.llm;

public interface LlmService {
    String llm(LlmRequest request);

    <T> T callAs(LlmRequest request, Class<T> classType);
}
