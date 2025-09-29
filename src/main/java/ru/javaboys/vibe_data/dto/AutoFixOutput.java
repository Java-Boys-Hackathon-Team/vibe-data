package ru.javaboys.vibe_data.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutoFixOutput {
    private String rationale;
    private List<String> newDdl;
    private List<String> migrations;
    private List<String> newSql;
}
