package ru.javaboys.vibe_data.api.dto;

import jakarta.validation.Valid;
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
public class ResultResponseDto {
    @NotEmpty
    @Valid
    private List<SqlBlockDto> ddl;

    @NotEmpty
    @Valid
    private List<SqlBlockDto> migrations;

    @NotEmpty
    @Valid
    private List<RewrittenQueryDto> queries;
}
