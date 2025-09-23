package ru.javaboys.vibe_data.domain.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

/**
 * Generic JSONB converter for JPA using Jackson and Bean Validation.
 * Ensures structure validation on write and strict deserialization on read.
 *
 * Important: returns PGobject(jsonb) so PostgreSQL receives the correct JDBC type
 * instead of VARCHAR, avoiding the "column is of type jsonb but expression is of type character varying" error.
 */
@Converter
public abstract class AbstractJsonAttributeConverter<T> implements AttributeConverter<T, Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    protected abstract TypeReference<T> typeReference();

    @Override
    public Object convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        validate(attribute);
        final String json;
        try {
            json = MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize attribute to JSON", e);
        }
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(json);
            return jsonObject;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to create PGobject for jsonb", e);
        }
    }

    @Override
    public T convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        String json;
        if (dbData instanceof PGobject pgo) {
            json = pgo.getValue();
        } else if (dbData instanceof String s) {
            json = s;
        } else {
            // Fallback: rely on toString (shouldn't normally happen)
            json = String.valueOf(dbData);
        }
        try {
            return MAPPER.readValue(json, typeReference());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to attribute", e);
        }
    }

    private void validate(T attribute) {
        // Validate the attribute itself
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(attribute);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        // If it's a collection, validate each element as well
        if (attribute instanceof Collection<?> c) {
            for (Object item : c) {
                if (item != null) {
                    Set<ConstraintViolation<Object>> v = VALIDATOR.validate(item);
                    if (!v.isEmpty()) {
                        throw new ConstraintViolationException(v);
                    }
                }
            }
        }
    }
}
