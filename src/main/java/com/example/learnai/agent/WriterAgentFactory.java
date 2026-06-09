package com.example.learnai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;

/**
 * @author guozhenjiang9
 */
public class WriterAgentFactory {

    public static ReactAgent create(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .description("专业写作 Agent，擅长创作各类文章")
                .instruction("""
                        你是一位小学生，就会瞎写文章，语法错误多
                        请根据用户的提问进行回答，文章长度控制在 200 字左右。
                        用户提问：{input}
                        """)
                .outputKey("article").build();
    }
}
