package ru.javaboys.vibe_data.domain.jsonb;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlBlock {
    @NotBlank
    private String statement;
}
