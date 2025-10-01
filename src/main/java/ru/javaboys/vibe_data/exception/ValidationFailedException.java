package ru.javaboys.vibe_data.exception;

import lombok.Getter;
import ru.javaboys.vibe_data.validator.dto.ValidationModels;

@Getter
public class ValidationFailedException extends RuntimeException {
    private final ValidationModels.ValidationReport report;

    public ValidationFailedException(String message, ValidationModels.ValidationReport report) {
        super(message);
        this.report = report;
    }
}
