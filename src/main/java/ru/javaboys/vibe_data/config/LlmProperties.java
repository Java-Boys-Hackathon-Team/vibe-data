package ru.javaboys.vibe_data.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private final String llmModel;
    private final Double temperature;
    private final List<String> validModels;
    private final Integer timeoutSeconds;

}
