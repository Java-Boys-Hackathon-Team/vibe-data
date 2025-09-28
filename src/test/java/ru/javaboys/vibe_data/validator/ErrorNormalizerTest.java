package ru.javaboys.vibe_data.validator;

import org.junit.jupiter.api.Test;
import ru.javaboys.vibe_data.validator.util.ErrorNormalizer;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorNormalizerTest {

    @Test
    void syntaxErrorMapping() {
        SQLException ex = new SQLException("Syntax error at line 1", "42000");
        var norm = ErrorNormalizer.map(ex);
        assertEquals("SYNTAX_ERROR", norm.code());
    }

    @Test
    void missingTableMapping() {
        SQLException ex = new SQLException("Table foo does not exist", "42S02");
        var norm = ErrorNormalizer.map(ex);
        assertEquals("MISSING_TABLE", norm.code());
    }

    @Test
    void timeoutMapping() {
        SQLException ex = new SQLException("Query timeout exceeded", "57014");
        var norm = ErrorNormalizer.map(ex);
        assertEquals("TIMEOUT", norm.code());
    }
}
