package ru.javaboys.vibe_data.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewTaskRequestDto {
    @NotBlank
    private String url;

    @NotEmpty
    @Valid
    private List<DdlStatementDto> ddl;

    @NotEmpty
    @Valid
    private List<QueryInputDto> queries;
}
