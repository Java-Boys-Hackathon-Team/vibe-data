package ru.javaboys.vibe_data.validator.util;

import java.sql.SQLException;

public class ErrorNormalizer {

    public record Normalized(String code, String hint) {}

    public static Normalized map(SQLException ex) {
        if (ex == null) return new Normalized("UNKNOWN", "no exception");
        String sqlState = ex.getSQLState();
        String msg = (ex.getMessage() == null ? "" : ex.getMessage()).toLowerCase();

        // SQLState based mappings (generic)
        if ("42000".equals(sqlState) || msg.contains("syntax error")) {
            return new Normalized("SYNTAX_ERROR", "проверьте диалект и кавычки идентификаторов");
        }
        if ("42S02".equals(sqlState) || msg.contains("table") && msg.contains("does not exist")) {
            return new Normalized("MISSING_TABLE", "уточните каталог/схему или добавьте IF EXISTS");
        }
        if (msg.contains("column") && msg.contains("does not exist")) {
            return new Normalized("MISSING_COLUMN", "проверьте регистр/кавычки идентификатора");
        }
        if (msg.contains("type mismatch") || msg.contains("cannot be cast")) {
            return new Normalized("TYPE_MISMATCH", "приведите типы явно или используйте CAST");
        }
        if ("28000".equals(sqlState) || msg.contains("access denied") || msg.contains("permission")) {
            return new Normalized("ACCESS_DENIED", "проверьте пользователя/роль и привилегии");
        }
        if (msg.contains("catalog") && msg.contains("not found")) {
            return new Normalized("CATALOG_NOT_FOUND", "уточните имя каталога в JDBC URL");
        }
        if (msg.contains("schema") && msg.contains("not found")) {
            return new Normalized("SCHEMA_NOT_FOUND", "создайте схему или проверьте имя");
        }
        if ("57014".equals(sqlState) || msg.contains("timeout")) {
            return new Normalized("TIMEOUT", "увеличьте таймаут или упростите запрос");
        }
        if ("08001".equals(sqlState) || msg.contains("i/o") || msg.contains("io exception")) {
            return new Normalized("IO_ERROR", "проверьте сеть и доступность Trino/MinIO");
        }
        if (msg.contains("schema diff") || msg.contains("mismatch in columns")) {
            return new Normalized("SCHEMA_DIFF", "синхронизируйте миграции и новую схему");
        }
        return new Normalized("UNKNOWN", "см. подробности ошибки");
    }
}