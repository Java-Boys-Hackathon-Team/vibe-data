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

import java.util.Collection;
import java.util.Set;

/**
 * Generic JSONB converter for JPA using Jackson and Bean Validation.
 * Ensures structure validation on write and strict deserialization on read.
 */
@Converter
public abstract class AbstractJsonAttributeConverter<T> implements AttributeConverter<T, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    protected abstract TypeReference<T> typeReference();

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        validate(attribute);
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize attribute to JSON", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, typeReference());
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
