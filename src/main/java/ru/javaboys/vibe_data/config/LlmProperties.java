package ru.javaboys.vibe_data.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private final String llmModel;
    private final Double temperature;
    private final List<String> validModels;

}
