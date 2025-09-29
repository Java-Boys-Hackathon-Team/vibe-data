package ru.javaboys.vibe_data.validator.spi;

import lombok.RequiredArgsConstructor;
import ru.javaboys.vibe_data.validator.api.ValidationModels.StorageKind;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal S3 provider placeholder.
 * Real implementation should use Trino CTAS and Iceberg $files via JDBC.
 */
@RequiredArgsConstructor
public class S3Provider implements StorageProvider {

    @Override
    public StorageKind kind() {
        return StorageKind.S3;
    }

    @Override
    public Inventory scan(InventoryRequest req) {
        String jdbcUrl = req.prodTrinoJdbcUrl();
        String catalog = req.catalog();

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL is empty, cannot scan catalog");
        }

        if (catalog == null || catalog.isBlank()) {
            throw new IllegalArgumentException("Catalog is empty, cannot scan");
        }

        List<String> tables = new ArrayList<>();
        String sql = """
            SELECT table_schema, table_name
            FROM %s.information_schema.tables
            WHERE table_schema NOT IN ('information_schema','sys','pg_catalog')
            ORDER BY table_schema, table_name
            """.formatted(catalog);

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String table = rs.getString("table_name");
                tables.add(schema + "." + table);
            }

            if (tables.isEmpty()) {
                return new Inventory("no tables found in catalog: " + catalog);
            }

            // Вернём все таблицы одной строкой
            return new Inventory(String.join(",", tables));

        } catch (SQLException e) {
            throw new RuntimeException("Failed to scan catalog " + catalog + " via Trino: " + e.getMessage(), e);
        }
    }

    @Override
    public SamplePlan planSample(Workload workload, SamplingSpec spec, Inventory inv) {
        return new SamplePlan("stub-plan percent=" + (spec.percent() == null ? 1.0 : spec.percent()));
    }

    @Override
    public SampleResult materializeSample(SamplePlan plan) {
        // In real code, create Iceberg tables in MinIO and return path
        return new SampleResult("s3://minio/warehouse/_sample/", 0);
    }
}
