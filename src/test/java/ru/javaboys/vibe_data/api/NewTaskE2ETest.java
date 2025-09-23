package ru.javaboys.vibe_data.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.StreamUtils;
import ru.javaboys.vibe_data.domain.Task;
import ru.javaboys.vibe_data.domain.TaskStatus;
import ru.javaboys.vibe_data.repository.TaskRepository;
import ru.javaboys.vibe_data.testutil.PostgresTestBase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "security.basic.username=testuser",
        "security.basic.password=testpass",
        "spring.ai.chat.client.enabled=false",
        "spring.ai.openai.enabled=false",
        "spring.ai.openai.api-key=dummy"
})
class NewTaskE2ETest extends PostgresTestBase {

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private TaskRepository taskRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /new should create a task and return 202 with {taskid}")
    @Sql(scripts = {"classpath:sql/clean.sql", "classpath:sql/seed.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void postNew_createsTask_persistsAndReturnsAccepted() throws IOException {
        // Arrange: load request JSON from external file
        String requestJson = readClasspathResourceAsString("json/new/new-task-request.json");

        // Prepare HTTP request with Basic Auth and JSON
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth("testuser", "testpass");
        HttpEntity<String> httpEntity = new HttpEntity<>(requestJson, headers);

        String url = "http://localhost:" + port + "/new";

        // Act: call endpoint
        ResponseEntity<String> response = testRestTemplate.getRestTemplate().exchange(url, HttpMethod.POST, httpEntity, String.class);

        // Assert: status code
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Assert: response body structure and values
        assertThat(response.getBody()).isNotNull();
        Map<String, Object> body = objectMapper.readValue(response.getBody(), new TypeReference<>() {});

        // Validate structure equals to expected structure file (only key present is taskid)
        String expectedStructureJson = readClasspathResourceAsString("json/new/new-task-response-structure.json");
        Map<String, Object> expectedStructure = objectMapper.readValue(expectedStructureJson, new TypeReference<>() {});
        assertThat(body.keySet()).as("Response keys").containsExactlyInAnyOrderElementsOf(expectedStructure.keySet());

        // Validate taskid exists and is valid UUID
        Object taskIdValue = body.get("taskid");
        assertThat(taskIdValue).isInstanceOf(String.class);
        UUID taskId = UUID.fromString(taskIdValue.toString()); // will throw if invalid

        // Validate the task is persisted in DB with RUNNING status
        Optional<Task> saved = taskRepository.findById(taskId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(TaskStatus.RUNNING);

        // Bonus: input should be present and linked
        assertThat(saved.get().getInput()).isNotNull();
        assertThat(saved.get().getInput().getPayload()).isNotNull();
    }

    private String readClasspathResourceAsString(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
}
