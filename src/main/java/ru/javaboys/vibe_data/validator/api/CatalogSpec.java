package ru.javaboys.vibe_data.validator.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CatalogSpec {
    private String catalog; // e.g., iceberg
    private String legacySchema; // legacy_public
    private String nextSchema; // next_public
    private String sampleSchema; // _sample
}