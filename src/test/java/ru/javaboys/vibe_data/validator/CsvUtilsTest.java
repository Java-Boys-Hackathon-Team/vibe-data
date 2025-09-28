package ru.javaboys.vibe_data.validator;

import org.junit.jupiter.api.Test;
import ru.javaboys.vibe_data.validator.util.CsvUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CsvUtilsTest {

    @Test
    void writeAndReadRoundtrip() throws Exception {
        Path tmp = Files.createTempFile("csvutils", ".csv");
        CsvUtils.writeWithHeader(tmp, List.of("a", "b"), List.of(List.of(1, "x"), List.of(2, "y")));
        var rows = CsvUtils.readWithHeader(tmp);
        assertEquals(2, rows.size());
        assertEquals(Map.of("a", "1", "b", "x"), rows.get(0));
        assertEquals(Map.of("a", "2", "b", "y"), rows.get(1));
    }
}
