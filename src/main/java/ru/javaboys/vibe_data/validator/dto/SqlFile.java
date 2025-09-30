package ru.javaboys.vibe_data.validator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SqlFile {
    private String name;
    private String content;

    /**
     * Позволяет присылать вместо объекта просто строку с SQL.
     * Пример: "newDdl": ["CREATE TABLE ...", "ALTER TABLE ..."]
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SqlFile(String content) {
        this.content = content;
        // Автогенерация имени, чтобы логи были читабельнее. Необязательное поле.
        this.name = "inline-" + System.currentTimeMillis() + ".sql";
    }
}