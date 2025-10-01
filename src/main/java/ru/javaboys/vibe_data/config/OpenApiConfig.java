package ru.javaboys.vibe_data.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    private static final String schemeName = "basicAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vibe Data API")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("https://api.vibe-data.javaboys.ru").description("Production server"),
                        new Server().url("http://localhost:8080").description("Local server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName,
                                new SecurityScheme()
                                        .name(schemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")));
    }
}