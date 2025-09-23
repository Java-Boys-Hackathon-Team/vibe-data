package ru.javaboys.vibe_data.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import ru.javaboys.vibe_data.domain.json.SqlBlock;

import java.util.List;

@Converter(autoApply = false)
public class SqlBlockListJsonConverter extends AbstractJsonAttributeConverter<List<SqlBlock>> {
    @Override
    protected TypeReference<List<SqlBlock>> typeReference() {
        return new TypeReference<>() {};
    }
}
