package ru.javaboys.vibe_data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

import ru.javaboys.utils.DotenvTestExecutionListener;

@SpringBootTest
@TestExecutionListeners(
        listeners = DotenvTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
class VibeDataApplicationTests {

    @Test
    void contextLoads() {
    }

}
