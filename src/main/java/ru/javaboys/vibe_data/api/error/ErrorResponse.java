package ru.javaboys.vibe_data.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private OffsetDateTime timestamp;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FieldValidationError> validationErrors;
}
