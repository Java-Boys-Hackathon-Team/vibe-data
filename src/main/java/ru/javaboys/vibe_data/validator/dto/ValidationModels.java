package ru.javaboys.vibe_data.validator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

public class ValidationModels {

    public enum StageName {
        VALIDATE_OLD_DDL,
        RUN_OLD_SQL,
        DEPLOY_NEW_DDL,
        APPLY_MIGRATIONS,
        RUN_NEW_SQL,
        COMPARE,
        SCHEMA_DIFF,
        INVENTORY,
        SAMPLE
    }

    public enum StageStatus { OK, ERROR }
    public enum ValidationStatus { OK, ERROR }

    public enum StorageKind { S3, HDFS, POSTGRES }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StageResult {
        private StageName name;
        private StageStatus status;
        private String details;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationError {
        private StageName stage;
        private String file; // optional
        private String code; // SYNTAX_ERROR, MISSING_TABLE, etc
        private String message;
        private String hint;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ArtifactsInfo {
        private String tablesSnapshotCsv;
        @Builder.Default
        private List<String> oldResults = new ArrayList<>();
        @Builder.Default
        private List<String> newResults = new ArrayList<>();
        @Builder.Default
        private List<String> diffs = new ArrayList<>();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StorageInfo {
        private StorageKind backend;
        private String sampleLocation;
        private Long bytesRead;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationRequest {
        private List<SqlFile> oldDdl;
        private List<SqlFile> oldSql;
        private List<SqlFile> newDdl;
        private List<SqlFile> newSql;
        private List<SqlFile> migrations;
        private String prodTrinoJdbcUrl;
        private SamplingSpec sampling;
        private CatalogSpec catalog;
        private ComparisonSpec comparison;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationReport {
        private ValidationStatus status;
        private List<StageResult> stages;
        private List<ValidationError> errors;
        private ArtifactsInfo artifacts;
        private StorageInfo storageInfo;
    }
}
