package ru.javaboys.vibe_data.llm;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import ru.javaboys.vibe_data.config.LlmProperties;
import ru.javaboys.vibe_data.monitoring.Monitored;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class LlmServiceImpl implements LlmService {

    private final LlmProperties llmProperties;
    private final ChatClient chatClient;

    @Monitored(key = "llm.call")
    @Override
    public String call(LlmRequest request) {
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = prepareChatClient(request);
        return executeWithTimeout(() -> chatClientRequestSpec.call().content());
    }

    @Monitored(key = "llm.call.as")
    @Override
    public <T> T callAs(LlmRequest request, Class<T> classType) {
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = prepareChatClient(request);
        return executeWithTimeout(() -> chatClientRequestSpec.call().entity(classType));
    }

    private <T> T executeWithTimeout(Callable<T> callable) {
        int timeoutSec = llmProperties.getTimeoutSeconds() != null ? llmProperties.getTimeoutSeconds() : 60;
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(timeoutSec))
                .cancelRunningFuture(true)
                .build();
        TimeLimiter timeLimiter = TimeLimiter.of(config);
        try {
            return timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }));
        } catch (Exception e) {
            // Propagate as-is to allow upper layers to handle TimeoutException explicitly
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    private ChatClient.ChatClientRequestSpec prepareChatClient(LlmRequest request) {
        List<Message> messages = new ArrayList<>();

        String systemMessage = request.getSystemMessage();
        if (systemMessage != null) {
            Message systemMsg;
            var systemVariables = request.getSystemVariables();
            if (systemVariables != null && !systemVariables.isEmpty()) {
                SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemMessage);
                systemMsg = systemPromptTemplate.createMessage(systemVariables);
            } else {
                systemMsg = new SystemMessage(systemMessage);
            }
            messages.add(systemMsg);
        }

        String userMessage = request.getUserMessage();
        if (userMessage != null) {
            Message userMsg;
            var userVariables = request.getUserVariables();
            if (userVariables != null && !userVariables.isEmpty()) {
                PromptTemplate promptTemplate = new PromptTemplate(userMessage);
                userMsg = promptTemplate.createMessage(userVariables);
            } else {
                userMsg = new UserMessage(userMessage);
            }
            messages.add(userMsg);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(Objects.requireNonNullElse(request.getLlmModel(), llmProperties.getLlmModel()))
                .temperature(Objects.requireNonNullElse(request.getTemperature(), llmProperties.getTemperature()))
                .build();
        Prompt prompt = new Prompt(messages, options);

        var chatClientRequestSpec = chatClient.prompt(prompt);

        String conversationId = request.getConversationId();
        if (conversationId != null) {
            chatClientRequestSpec = chatClientRequestSpec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }

        List<Object> tools = request.getTools();
        if (tools != null && !tools.isEmpty()) {
            chatClientRequestSpec = chatClientRequestSpec.tools(tools.toArray());
        }

        return chatClientRequestSpec;
    }
}
