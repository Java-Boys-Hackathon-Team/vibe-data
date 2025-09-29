package ru.javaboys.vibe_data.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TrinoLocalDatasourceConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.trino-local")
    public DataSourceProperties trinoLocalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "trinoLocalDataSource")
    @ConfigurationProperties("spring.datasource.trino-local.hikari")
    public DataSource trinoLocalDataSource() {
        return trinoLocalDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean(name = "trinoLocalJdbcTemplate")
    public JdbcTemplate trinoLocalJdbcTemplate(@Qualifier("trinoLocalDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
