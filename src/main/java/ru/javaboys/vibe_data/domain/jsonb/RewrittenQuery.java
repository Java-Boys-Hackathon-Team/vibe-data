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
public class RewrittenQuery {
    @NotBlank
    private String queryid;

    @NotBlank
    private String query;
}
