package ru.javaboys.vibe_data.validator.util;

import java.util.*;

public class ToleranceComparator {

    public record Diff(String key, String metric, double expected, double actual, double relPct){ }

    public static List<Diff> compareWithTolerance(Map<String, Double> expected, Map<String, Double> actual, double tolerancePct) {
        List<Diff> diffs = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(expected.keySet());
        keys.addAll(actual.keySet());
        for (String k : keys) {
            double e = expected.getOrDefault(k, 0d);
            double a = actual.getOrDefault(k, 0d);
            double denom = Math.max(Math.abs(e), 1e-9);
            double rel = Math.abs(a - e) / denom * 100.0;
            if (rel > tolerancePct) {
                diffs.add(new Diff(k, "value", e, a, rel));
            }
        }
        return diffs;
    }
}