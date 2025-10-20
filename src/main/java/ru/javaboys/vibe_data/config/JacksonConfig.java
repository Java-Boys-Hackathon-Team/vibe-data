package ru.javaboys.vibe_data.config;

import java.lang.reflect.Field;

import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class JacksonConfig {
    
    @PostConstruct
    public void fixModelOptionsUtilsObjectMapper() {
        try {
            // Get ModelOptionsUtils class
            Class<?> modelOptionsUtilsClass = Class.forName("org.springframework.ai.model.ModelOptionsUtils");
            
            // Get OBJECT_MAPPER field
            Field objectMapperField = modelOptionsUtilsClass.getDeclaredField("OBJECT_MAPPER");
            objectMapperField.setAccessible(true);
            
            // Get current ObjectMapper instance
            ObjectMapper currentMapper = (ObjectMapper) objectMapperField.get(null);
            
            // Create JavaTimeModule instance directly, avoiding class loader issues
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            
            // Register module
            currentMapper.registerModule(javaTimeModule);
            
            log.info(">>> Successfully registered JavaTimeModule to ModelOptionsUtils.OBJECT_MAPPER");
        } catch (Exception e) {
            log.error(">>> Failed to modify ModelOptionsUtils.OBJECT_MAPPER: " + e.getMessage(), e);
        }
    }
}