package ru.javaboys.vibe_data.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TrinoDatasourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.trino")
    public DataSourceProperties trinoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.trino.hikari")
    public DataSource trinoDataSource() {
        return trinoDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean
    public JdbcTemplate trinoJdbcTemplate(@Qualifier("trinoDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

}