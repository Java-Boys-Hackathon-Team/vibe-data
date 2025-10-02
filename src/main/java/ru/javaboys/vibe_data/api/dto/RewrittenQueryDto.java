package ru.javaboys.vibe_data.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Переписанный запрос")
public class RewrittenQueryDto {
    @NotBlank
    @Schema(name = "Идентификатор")
    private String queryid;

    @NotBlank
    @Schema(name = "запрос")
    private String query;
}
