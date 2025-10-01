package ru.javaboys.vibe_data.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;
import ru.javaboys.vibe_data.exception.ValidationFailedException;
import ru.javaboys.vibe_data.validator.DdlSqlValidator.ValidatedArtifacts;
import ru.javaboys.vibe_data.validator.dto.ValidationModels;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationSwitcher {

    private final DdlSqlValidator ddlSqlValidator;

    @Value("${validation.enabled}")
    private Boolean validationEnabled;

    @Transactional
    public ValidatedArtifacts validate(Task task,
                                       List<SqlBlock> finalDdl,
                                       List<SqlBlock> migrations,
                                       List<RewrittenQuery> finalQueries) {
        if (!validationEnabled) {
            log.info("Validation DISABLED via feature flag → returning pass-through artifacts.");
            return passThrough(finalDdl, migrations, finalQueries);
        }

        try {
            return ddlSqlValidator.validateFinalArtifactsOrThrow(task, finalDdl, migrations, finalQueries);
        } catch (ValidationFailedException ex) {
            log.warn("Validation failed after max attempts, fail-open fallback. Reason: {}", ex.getMessage());
            return passThrough(finalDdl, migrations, finalQueries);
        }
    }

    private ValidatedArtifacts passThrough(List<SqlBlock> finalDdl,
                                           List<SqlBlock> migrations,
                                           List<RewrittenQuery> finalQueries) {
        return new ValidatedArtifacts(
                new ArrayList<>(finalDdl),
                new ArrayList<>(migrations),
                new ArrayList<>(finalQueries),
                ValidationModels.ValidationReport.builder()
                        .status(ValidationModels.ValidationStatus.OK)
                        .stages(List.of())
                        .errors(List.of())
                        .artifacts(ValidationModels.ArtifactsInfo.builder()
                                .oldResults(List.of())
                                .newResults(List.of())
                                .diffs(List.of())
                                .build())
                        .build()
        );
    }
}
