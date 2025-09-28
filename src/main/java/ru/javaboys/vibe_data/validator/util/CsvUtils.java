package ru.javaboys.vibe_data.validator.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvUtils {

    public static List<Map<String, String>> readWithHeader(Path path) throws IOException {
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(in);
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord r : records) {
                Map<String, String> row = new LinkedHashMap<>();
                r.toMap().forEach(row::put);
                rows.add(row);
            }
            return rows;
        }
    }

    public static void writeWithHeader(Path path, List<String> headers, List<List<Object>> rows) throws IOException {
        try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8); CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build())) {
            for (List<Object> row : rows) {
                printer.printRecord(row);
            }
        }
    }
}