package ru.javaboys.vibe_data.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import ru.javaboys.vibe_data.domain.json.TaskInputPayload;

@Converter(autoApply = false)
public class TaskInputPayloadJsonConverter extends AbstractJsonAttributeConverter<TaskInputPayload> {
    @Override
    protected TypeReference<TaskInputPayload> typeReference() {
        return new TypeReference<>() {};
    }
}
