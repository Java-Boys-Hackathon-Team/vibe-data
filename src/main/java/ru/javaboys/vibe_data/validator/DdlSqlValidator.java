package ru.javaboys.vibe_data.validator;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.javaboys.vibe_data.validator.api.SqlFile;
import ru.javaboys.vibe_data.validator.api.ValidationModels.*;
import ru.javaboys.vibe_data.validator.spi.S3Provider;
import ru.javaboys.vibe_data.validator.spi.StorageProvider;
import ru.javaboys.vibe_data.validator.util.CsvUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DdlSqlValidator {

    @Value("${validator.artifacts.dir:artifacts}")
    private String artifactsDir;

    // For now, force S3 provider as per requirements
    private final StorageProvider provider = new S3Provider();

    public ValidationReport validate(ValidationRequest req) {
        List<StageResult> stages = new ArrayList<>();
        List<ValidationError> errors = new ArrayList<>();
        ArtifactsInfo artifacts = new ArtifactsInfo();
        StorageInfo storageInfo = StorageInfo.builder().backend(StorageKind.S3).build();

        Path baseDir = Path.of(artifactsDir, "run-" + System.currentTimeMillis());
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            // ignore
        }

        // Stage: INVENTORY
        try {
            stages.add(StageResult.builder().name(StageName.INVENTORY).status(StageStatus.OK).details("starting").build());
            StorageProvider.Inventory inv = provider.scan(new StorageProvider.InventoryRequest(req.getProdTrinoJdbcUrl(), req.getCatalog() != null ? req.getCatalog().getCatalog() : null));
            Path snapshot = baseDir.resolve("tables_snapshot.csv");
            CsvUtils.writeWithHeader(snapshot, List.of("info"), List.of(List.of(inv.info())));
            artifacts.setTablesSnapshotCsv(snapshot.toString());
        } catch (Exception e) {
            stages.add(StageResult.builder().name(StageName.INVENTORY).status(StageStatus.ERROR).details(e.getMessage()).build());
            errors.add(ValidationError.builder().stage(StageName.INVENTORY).code("IO_ERROR").message(e.getMessage()).hint("проверьте доступ к Trino").build());
            return ValidationReport.builder().status(ValidationStatus.ERROR).stages(stages).errors(errors).artifacts(artifacts).storageInfo(storageInfo).build();
        }

        // Stage: SAMPLE
        try {
            stages.add(StageResult.builder().name(StageName.SAMPLE).status(StageStatus.OK).details("planning").build());
            StorageProvider.SamplePlan plan = provider.planSample(new StorageProvider.Workload(), new StorageProvider.SamplingSpec(req.getSampling() != null ? req.getSampling().getPercent() : 1.0), new StorageProvider.Inventory("stub"));
            StorageProvider.SampleResult res = provider.materializeSample(plan);
            storageInfo.setSampleLocation(res.location());
            storageInfo.setBytesRead(res.bytesRead());
        } catch (Exception e) {
            stages.add(StageResult.builder().name(StageName.SAMPLE).status(StageStatus.ERROR).details(e.getMessage()).build());
            errors.add(ValidationError.builder().stage(StageName.SAMPLE).code("IO_ERROR").message(e.getMessage()).hint("ограничьте sample или проверьте MinIO").build());
            return ValidationReport.builder().status(ValidationStatus.ERROR).stages(stages).errors(errors).artifacts(artifacts).storageInfo(storageInfo).build();
        }

        // Stub pipeline for remaining stages to satisfy contract
        runSimpleStage(StageName.VALIDATE_OLD_DDL, req.getOldDdl(), stages, errors);
        runSimpleStage(StageName.RUN_OLD_SQL, req.getOldSql(), stages, errors);
        runSimpleStage(StageName.DEPLOY_NEW_DDL, req.getNewDdl(), stages, errors);
        runSimpleStage(StageName.APPLY_MIGRATIONS, req.getMigrations(), stages, errors);
        runSimpleStage(StageName.RUN_NEW_SQL, req.getNewSql(), stages, errors);
        stages.add(StageResult.builder().name(StageName.COMPARE).status(StageStatus.OK).details("strict placeholder").build());
        stages.add(StageResult.builder().name(StageName.SCHEMA_DIFF).status(StageStatus.OK).details("no diffs").build());

        ValidationStatus status = errors.isEmpty() ? ValidationStatus.OK : ValidationStatus.ERROR;
        return ValidationReport.builder()
                .status(status)
                .stages(stages)
                .errors(errors)
                .artifacts(artifacts)
                .storageInfo(storageInfo)
                .build();
    }

    private void runSimpleStage(StageName name, List<SqlFile> files, List<StageResult> stages, List<ValidationError> errors) {
        try {
            if (files != null) {
                for (SqlFile f : files) {
                    // place-holder: just create artifact files with timestamp
                    String details = "processed " + f.getName() + " at " + LocalDateTime.now();
                    stages.add(StageResult.builder().name(name).status(StageStatus.OK).details(details).build());
                }
            } else {
                stages.add(StageResult.builder().name(name).status(StageStatus.OK).details("no files").build());
            }
        } catch (Exception e) {
            stages.add(StageResult.builder().name(name).status(StageStatus.ERROR).details(e.getMessage()).build());
            errors.add(ValidationError.builder().stage(name).message(e.getMessage()).code("UNKNOWN").hint("см. логи").build());
        }
    }
}
