package ru.javaboys.vibe_data.domain.jsonb;

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
public class TaskInputPayload {
    @NotBlank
    private String url; // JDBC URL

    @NotEmpty
    @Valid
    private List<DdlStatement> ddl;

    @NotEmpty
    @Valid
    private List<QueryInput> queries;
}
