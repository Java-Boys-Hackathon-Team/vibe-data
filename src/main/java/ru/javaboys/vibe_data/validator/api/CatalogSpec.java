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
    private String catalog;
    private String legacySchema;
    private String nextSchema;
    private String sampleSchema;
}