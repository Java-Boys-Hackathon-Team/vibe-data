package ru.javaboys.vibe_data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

import ru.javaboys.utils.DotenvTestExecutionListener;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = {
        "security.basic.username=testuser",
        "security.basic.password=testpass",
        "spring.ai.chat.client.enabled=false",
        "spring.ai.openai.enabled=false",
        "spring.ai.openai.api-key=dummy"
})
@TestExecutionListeners(
        listeners = DotenvTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
class VibeDataApplicationTests extends ru.javaboys.vibe_data.testutil.PostgresTestBase {

    @Test
    void contextLoads() {
    }

}
