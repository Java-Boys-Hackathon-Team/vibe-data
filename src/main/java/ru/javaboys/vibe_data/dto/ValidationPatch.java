package ru.javaboys.vibe_data.dto;

import java.util.List;

/**
 * Патч, возвращаемый LLM для автоисправления артефактов валидации.
 */
public class ValidationPatch {
    private List<String> newDdl;
    private List<String> migrations;
    private List<String> newSql;

    public List<String> getNewDdl() { return newDdl; }
    public void setNewDdl(List<String> newDdl) { this.newDdl = newDdl; }

    public List<String> getMigrations() { return migrations; }
    public void setMigrations(List<String> migrations) { this.migrations = migrations; }

    public List<String> getNewSql() { return newSql; }
    public void setNewSql(List<String> newSql) { this.newSql = newSql; }
}