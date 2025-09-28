package ru.javaboys.vibe_data.validator.spi;

import ru.javaboys.vibe_data.validator.api.ValidationModels.StorageKind;

public interface StorageProvider {
    StorageKind kind();

    default Inventory scan(InventoryRequest req) {
        throw new UnsupportedOperationException("scan not implemented for " + kind());
    }

    default SamplePlan planSample(Workload workload, SamplingSpec spec, Inventory inv) {
        throw new UnsupportedOperationException("planSample not implemented for " + kind());
    }

    default SampleResult materializeSample(SamplePlan plan) {
        throw new UnsupportedOperationException("materializeSample not implemented for " + kind());
    }

    // Inner model types (sufficient for stub and keeping code size minimal)
    record Inventory(String info) {}
    record InventoryRequest(String prodTrinoJdbcUrl, String catalog) {}
    record Workload() {}
    record SamplingSpec(Double percent) {}
    record SamplePlan(String description) {}
    record SampleResult(String location, long bytesRead) {}
}