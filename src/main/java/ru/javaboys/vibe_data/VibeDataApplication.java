package ru.javaboys.vibe_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

import ru.javaboys.vibe_data.config.LlmProperties;

@EnableCaching
@EnableConfigurationProperties({LlmProperties.class})
@SpringBootApplication
public class VibeDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(VibeDataApplication.class, args);
    }

}
