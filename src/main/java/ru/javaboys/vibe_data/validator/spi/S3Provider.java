package ru.javaboys.vibe_data.validator.spi;

import lombok.RequiredArgsConstructor;
import ru.javaboys.vibe_data.validator.api.ValidationModels.StorageKind;

/**
 * Minimal S3 provider placeholder.
 * Real implementation should use Trino CTAS and Iceberg $files via JDBC.
 */
@RequiredArgsConstructor
public class S3Provider implements StorageProvider {

    @Override
    public StorageKind kind() {
        return StorageKind.S3;
    }

    @Override
    public Inventory scan(InventoryRequest req) {
        // TODO: use Trino to query information_schema and iceberg.$files
        return new Inventory("stub-scan:" + req.catalog());
    }

    @Override
    public SamplePlan planSample(Workload workload, SamplingSpec spec, Inventory inv) {
        return new SamplePlan("stub-plan percent=" + (spec.percent() == null ? 1.0 : spec.percent()));
    }

    @Override
    public SampleResult materializeSample(SamplePlan plan) {
        // In real code, create Iceberg tables in MinIO and return path
        return new SampleResult("s3://minio/warehouse/_sample/", 0);
    }
}
