package com.example.learnai.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author guozhenjiang9
 */
@Component
public class ChatModelFactory {

    private static final ConcurrentHashMap<String, ChatModel> chatModelMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();


    @PostConstruct
    public void init() {
        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .apiKey("sk-e5e21f39aa874bcd8d672a3ecfc90a4c")
                .build();

        ChatModel deepSeekModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(DeepSeekChatOptions.builder().model("deepseek-v4-pro").build())
                .build();

        chatModelMap.put("deepSeekV4Pro", deepSeekModel);
        ChatClient chatClient = ChatClient.builder(deepSeekModel).defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
                // 实现 Logger 的 Advisor
                .defaultAdvisors(new SimpleLoggerAdvisor())
                // 设置 ChatClient 中 ChatModel 的 Options 参数
                .defaultOptions(DeepSeekChatOptions.builder().temperature(0.7d).build()).build();
        chatClientMap.put("deepSeekV4ProChatClient", chatClient);
    }
    public ChatClient getChatClient(String name) {
        return chatClientMap.get(name);
    }

}
