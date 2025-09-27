package ru.javaboys.vibe_data.api.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FieldValidationError {
    private String field;
    private String message;
}