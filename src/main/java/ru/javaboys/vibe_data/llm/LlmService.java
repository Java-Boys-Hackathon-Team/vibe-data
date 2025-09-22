package ru.javaboys.vibe_data.llm;

public interface LlmService {
    String call(LlmRequest request);

    <T> T callAs(LlmRequest request, Class<T> classType);
}
