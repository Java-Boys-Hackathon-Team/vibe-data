package ru.javaboys.vibe_data.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AutoFixOutput {
    private String rationale;

    // Новые однозначные поля
    @JsonProperty("OUT_DDL")
    @JsonAlias({"newDdl"}) // совместимость со старым контрактом
    private List<String> outDdl;

    @JsonProperty("OUT_MIGRATIONS")
    @JsonAlias({"migrations"}) // совместимость
    private List<String> outMigrations;

    @JsonProperty("OUT_SQL")
    @JsonAlias({"newSql"}) // совместимость
    private List<String> outSql;
}
