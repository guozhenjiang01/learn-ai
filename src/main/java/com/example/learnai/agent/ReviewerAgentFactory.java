package com.example.learnai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;

/**
 * @author guozhenjiang9
 */
public class ReviewerAgentFactory {

    static ReactAgent create(ChatModel chatModel) {
        return ReactAgent.builder().name("reviewer_agent")
                .model(chatModel)
                .description("专业评审 Agent，擅长修改和润色文章")
                .instruction("""
                        你是一位资深评论家，擅长对文章进行评审和修改。
                        待评审文章：{article}
                        请确保：
                        1. 文章包含对西湖风景的描述
                        2. 语言优美流畅
                        3. 最终只返回修改后的文章，不要包含任何评论信息
                        """)
                .outputKey("reviewed_article").build();
    }
}
