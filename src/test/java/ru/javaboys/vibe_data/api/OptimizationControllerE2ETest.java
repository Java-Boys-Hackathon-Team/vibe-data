package ru.javaboys.vibe_data.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.javaboys.vibe_data.api.dto.NewOptimizationRequestDto;
import ru.javaboys.vibe_data.api.dto.NewOptimizationResponseDto;
import ru.javaboys.vibe_data.api.dto.OptimizationDto;

@Sql(statements = {
        "TRUNCATE TABLE optimization RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.MethodName.class)
class OptimizationControllerE2ETest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vibedata")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.client.type", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> "dummy");
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        this.baseUrl = "http://localhost:" + port + "/api/v1/optimizations";
    }

    @Test
    @DisplayName("Create optimization returns 202 Accepted and id")
    void test01_createOptimization() {
        Map<String, String> payload = Map.of("text", "Speed up Iceberg table queries");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<NewOptimizationResponseDto> response = restTemplate.postForEntity(baseUrl + "/new", request, NewOptimizationResponseDto.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertThat(response.getBody())
                .extracting(NewOptimizationResponseDto::getId)
                .isNotNull();
    }

    @Test
    @DisplayName("Get by id returns created optimization with active=true")
    void test02_getById() {
        // First create
        NewOptimizationRequestDto payload = NewOptimizationRequestDto.builder()
                .text("Optimize partitions")
                .build();
        OptimizationDto optimization = createOptimization(payload);

        ResponseEntity<OptimizationDto> getResp = restTemplate.getForEntity(baseUrl + "/" + optimization.getId(), OptimizationDto.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertThat(getResp.getBody())
                .usingRecursiveComparison()
                .isEqualTo(
                        OptimizationDto.builder()
                                .id(optimization.getId())
                                .active(true)
                                .text(payload.getText())
                                .build()
                );
    }

    @Test
    @DisplayName("Activate and deactivate toggle the active flag")
    void test03_activateDeactivate() {

        NewOptimizationRequestDto payload = NewOptimizationRequestDto.builder()
                .text("Enable zstd compression")
                .build();
        OptimizationDto optimization = createOptimization(payload);

        // deactivate
        ResponseEntity<OptimizationDto> deResp = restTemplate.postForEntity(baseUrl + "/" + optimization.getId() + "/deactivate", null, OptimizationDto.class);
        assertEquals(HttpStatus.OK, deResp.getStatusCode());
        assertThat(deResp.getBody())
                .usingRecursiveComparison()
                .isEqualTo(
                        OptimizationDto.builder()
                                .id(optimization.getId())
                                .active(false)
                                .text(payload.getText())
                                .build()
                );

        // activate
        ResponseEntity<OptimizationDto> acResp = restTemplate.postForEntity(baseUrl + "/" + optimization.getId() + "/activate", null, OptimizationDto.class);
        assertEquals(HttpStatus.OK, acResp.getStatusCode());
        assertThat(acResp.getBody())
                .usingRecursiveComparison()
                .isEqualTo(
                        OptimizationDto.builder()
                                .id(optimization.getId())
                                .active(true)
                                .text(payload.getText())
                                .build()
                );
    }

    @Test
    @DisplayName("Find all active returns only active optimizations")
    void test04_findAllActive() {
        NewOptimizationRequestDto activePayload = NewOptimizationRequestDto.builder()
                .text("Keep small files under control")
                .build();
        OptimizationDto active = createOptimization(activePayload);

        NewOptimizationRequestDto inactivePayload = NewOptimizationRequestDto.builder()
                .text("Old rule")
                .build();
        OptimizationDto inactive = createOptimization(inactivePayload);
        // deactivate second
        inactive = restTemplate.postForEntity(baseUrl + "/" + inactive.getId() + "/deactivate", null, OptimizationDto.class).getBody();

        ResponseEntity<List<OptimizationDto>> listResp = restTemplate.exchange(baseUrl, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertThat(listResp.getBody())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(
                        OptimizationDto.builder()
                                .id(active.getId())
                                .active(true)
                                .text(active.getText())
                                .build()
                );
    }

    private OptimizationDto createOptimization(NewOptimizationRequestDto payload) {
        ResponseEntity<NewOptimizationResponseDto> createResp = restTemplate.postForEntity(baseUrl + "/new", payload, NewOptimizationResponseDto.class);
        assertEquals(HttpStatus.ACCEPTED, createResp.getStatusCode());
        return OptimizationDto.builder()
                .id(createResp.getBody().getId())
                .text(payload.getText())
                .active(true).build();
    }
}
