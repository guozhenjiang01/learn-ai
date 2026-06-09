package com.example.learnai.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

/**
 * @author guozhenjiang9
 */
public class BlogWorkflowFactory {

    public static SequentialAgent create(ChatModel chatModel) {
        // 创建子 Agent
        ReactAgent writerAgent = WriterAgentFactory.create(chatModel);
        ReactAgent reviewerAgent = ReviewerAgentFactory.create(chatModel);
        // 创建顺序执行的 Multi-agent
        return SequentialAgent.builder()
                .name("blog_workflow")
                .description("写作与评审工作流：先写文章，再评审修改")
                .subAgents(List.of(writerAgent, reviewerAgent)).build();
    }
}
