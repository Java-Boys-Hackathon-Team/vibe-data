package ru.javaboys.vibe_data.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.javaboys.vibe_data.agent.QueryOptimizerAgent;
import ru.javaboys.vibe_data.api.dto.DdlStatementDto;
import ru.javaboys.vibe_data.api.dto.NewTaskRequestDto;
import ru.javaboys.vibe_data.api.dto.NewTaskResponseDto;
import ru.javaboys.vibe_data.api.dto.QueryInputDto;
import ru.javaboys.vibe_data.api.dto.ResultResponseDto;
import ru.javaboys.vibe_data.api.dto.StatusResponseDto;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskResult;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.domain.jsonb.RewrittenQuery;
import ru.javaboys.vibe_data.domain.jsonb.SqlBlock;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(statements = {
        "TRUNCATE TABLE task_result, task_input, tasks RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TaskControllerE2ETest {

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

    @MockitoBean
    QueryOptimizerAgent queryOptimizerAgent;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        this.baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Create task returns 202 Accepted and taskid")
    void createTask_returns202AndId() {
        NewTaskRequestDto payload = validRequest();

        when(queryOptimizerAgent.optimize(ArgumentMatchers.any(Task.class)))
                .thenAnswer(inv -> buildResult(inv.getArgument(0)));

        ResponseEntity<NewTaskResponseDto> response = restTemplate
                .postForEntity(baseUrl + "/new", payload, NewTaskResponseDto.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertDoesNotThrow(() -> UUID.fromString(response.getBody().getTaskid().toString()));
    }

    @Test
    @DisplayName("Status transitions to DONE after async processing (mocked agent)")
    void status_becomesDone() throws Exception {
        NewTaskRequestDto payload = validRequest();
        when(queryOptimizerAgent.optimize(ArgumentMatchers.any(Task.class)))
                .thenAnswer(inv -> buildResult(inv.getArgument(0)));

        UUID id = createTaskAndGetId(payload);

        TaskStatus status = pollStatusUntilDone(id);
        assertEquals(TaskStatus.DONE, status);
    }

    @Test
    @DisplayName("Get result returns mocked result when status is DONE")
    void getResult_returnsMockedData() throws Exception {
        NewTaskRequestDto payload = validRequest();
        when(queryOptimizerAgent.optimize(ArgumentMatchers.any(Task.class)))
                .thenAnswer(inv -> buildResult(inv.getArgument(0)));

        UUID id = createTaskAndGetId(payload);
        TaskStatus status = pollStatusUntilDone(id);
        assertEquals(TaskStatus.DONE, status);

        ResponseEntity<ResultResponseDto> resp = restTemplate
                .getForEntity(baseUrl + "/getresult?task_id=" + id, ResultResponseDto.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertThat(resp.getBody().getDdl()).isNotEmpty();
        assertThat(resp.getBody().getMigrations()).isNotEmpty();
        assertThat(resp.getBody().getQueries()).isNotEmpty();
    }

    @Test
    @DisplayName("Invalid LLM model should return 400 Bad Request")
    void invalidModel_returns400() {
        NewTaskRequestDto base = validRequest();
        NewTaskRequestDto payload = NewTaskRequestDto.builder()
                .llmModel("non-existent-model")
                .temperature(base.getTemperature())
                .url(base.getUrl())
                .ddl(base.getDdl())
                .queries(base.getQueries())
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity(baseUrl + "/new", payload, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("Invalid model name"));
    }

    // ---- helpers ----
    private UUID createTaskAndGetId(NewTaskRequestDto payload) {
        ResponseEntity<NewTaskResponseDto> resp = restTemplate
                .postForEntity(baseUrl + "/new", payload, NewTaskResponseDto.class);
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        return resp.getBody().getTaskid();
    }

    private TaskStatus pollStatusUntilDone(UUID id) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            ResponseEntity<StatusResponseDto> resp = restTemplate
                    .getForEntity(baseUrl + "/status?task_id=" + id, StatusResponseDto.class);
            assertEquals(HttpStatus.OK, resp.getStatusCode());
            TaskStatus st = resp.getBody().getStatus();
            if (st == TaskStatus.DONE) return st;
            Thread.sleep(200);
        }
        ResponseEntity<StatusResponseDto> last = restTemplate
                .getForEntity(baseUrl + "/status?task_id=" + id, StatusResponseDto.class);
        return last.getBody().getStatus();
    }

    private NewTaskRequestDto validRequest() {
        return NewTaskRequestDto.builder()
                .llmModel(null)
                .temperature(0.1)
                .url("jdbc:trino://localhost:18080/iceberg")
                .ddl(List.of(
                        DdlStatementDto.builder().statement("CREATE TABLE t(x int)").build()
                ))
                .queries(List.of(
                        QueryInputDto.builder().queryid("q1").query("select 1").runquantity(1).executiontime(1).build()
                ))
                .build();
    }

    private TaskResult buildResult(Task task) {
        return TaskResult.builder()
                .task(task)
                .ddl(List.of(SqlBlock.builder().statement("-- ddl").build()))
                .migrations(List.of(SqlBlock.builder().statement("-- migration").build()))
                .queries(List.of(RewrittenQuery.builder().queryid("q1").query("select 1").build()))
                .build();
    }
}
