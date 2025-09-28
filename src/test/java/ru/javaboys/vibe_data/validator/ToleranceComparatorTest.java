package ru.javaboys.vibe_data.validator;

import org.junit.jupiter.api.Test;
import ru.javaboys.vibe_data.validator.util.ToleranceComparator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ToleranceComparatorTest {

    @Test
    void compareWithinTolerance() {
        Map<String, Double> a = Map.of("k1", 100.0);
        Map<String, Double> b = Map.of("k1", 102.0);
        List<ToleranceComparator.Diff> diffs = ToleranceComparator.compareWithTolerance(a, b, 3.0);
        assertTrue(diffs.isEmpty(), "2% diff within 3% tolerance");
    }

    @Test
    void compareExceedsTolerance() {
        Map<String, Double> a = Map.of("k1", 100.0);
        Map<String, Double> b = Map.of("k1", 106.0);
        List<ToleranceComparator.Diff> diffs = ToleranceComparator.compareWithTolerance(a, b, 3.0);
        assertEquals(1, diffs.size(), "6% diff exceeds 3% tolerance");
    }
}
