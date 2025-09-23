package ru.javaboys.vibe_data.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;
import ru.javaboys.vibe_data.domain.json.RewrittenQuery;

import java.util.List;

@Converter(autoApply = false)
public class RewrittenQueryListJsonConverter extends AbstractJsonAttributeConverter<List<RewrittenQuery>> {
    @Override
    protected TypeReference<List<RewrittenQuery>> typeReference() {
        return new TypeReference<>() {};
    }
}
