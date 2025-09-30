package ru.javaboys.vibe_data.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewTaskRequestDto {

    private String llmModel;

    @Min(0)
    @Max(1)
    private Double temperature;

    @NotBlank
    private String url;

    @NotEmpty
    @Valid
    private List<DdlStatementDto> ddl;

    @NotEmpty
    @Valid
    private List<QueryInputDto> queries;
}
