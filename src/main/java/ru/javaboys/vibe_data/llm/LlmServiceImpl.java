package ru.javaboys.vibe_data.llm;

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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class LlmServiceImpl implements LlmService {

    private final ChatClient chatClient;

    @Override
    public String llmCall(String conversationId,
                          String systemMessage,
                          Map<String, Object> systemVariables,
                          String userMessage,
                          Map<String, Object> userVariables,
                          List<Object> tools) {

        ChatClient.ChatClientRequestSpec chatClientRequestSpec = prepareChatClient(
                conversationId,
                systemMessage,
                systemVariables,
                userMessage,
                userVariables,
                tools
        );

        return chatClientRequestSpec
                .call()
                .content();
    }

    @Override
    public <T> T llmCallToBean(String conversationId,
                               String systemMessage,
                               Map<String, Object> systemVariables,
                               String userMessage,
                               Map<String, Object> userVariables,
                               List<Object> tools,
                               Class<T> classType) {

        ChatClient.ChatClientRequestSpec chatClientRequestSpec = prepareChatClient(
                conversationId,
                systemMessage,
                systemVariables,
                userMessage,
                userVariables,
                tools
        );

        return chatClientRequestSpec
                .call()
                .entity(classType);
    }

    private ChatClient.ChatClientRequestSpec prepareChatClient(String conversationId,
                                                               String systemMessage,
                                                               Map<String, Object> systemVariables,
                                                               String userMessage,
                                                               Map<String, Object> userVariables,
                                                               List<Object> tools) {
        List<Message> messages = new ArrayList<>();

        if (systemMessage != null) {
            Message systemMsg;
            if (systemVariables != null && !systemVariables.isEmpty()) {
                SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemMessage);
                systemMsg = systemPromptTemplate.createMessage(systemVariables);
            } else {
                systemMsg = new SystemMessage(systemMessage);
            }
            messages.add(systemMsg);
        }

        if (userMessage != null) {
            Message userMsg;
            if (userVariables != null && !userVariables.isEmpty()) {
                PromptTemplate promptTemplate = new PromptTemplate(userMessage);
                userMsg = promptTemplate.createMessage(userVariables);
            } else {
                userMsg = new UserMessage(userMessage);
            }
            messages.add(userMsg);
        }

        Prompt prompt = new Prompt(messages);

        var chatClientRequestSpec = chatClient.prompt(prompt);

        if (conversationId != null) {
            chatClientRequestSpec = chatClientRequestSpec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }

        if (tools != null && !tools.isEmpty()) {
            chatClientRequestSpec = chatClientRequestSpec.tools(tools);
        }

        return chatClientRequestSpec;
    }
}
