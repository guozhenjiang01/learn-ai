package com.example.learnai.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author guozhenjiang9
 */
@Slf4j
public class MultiAgentDemo {

    public static void main(String[] args) throws Exception {
        // 构建 DashScope API（千问大模型）
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey("sk-3566bb7ecf20404ba9fbbd16db1ca564")
                .build();

        // 构建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().model("qwen-max").build())
                .build();

        // 创建工作流
        SequentialAgent workflow = BlogWorkflowFactory.create(chatModel);
        // 执行任务
        Optional<OverAllState> result = workflow.invoke("帮我写一篇 100 字左右的西湖散文");
        // 处理结果
        if (result.isPresent()) {
            OverAllState state = result.get();
            state.value("article").ifPresent(article -> {
                System.out.println("原始文章: " + article);
            });
            state.value("reviewed_article").ifPresent(reviewedArticle -> {
                System.out.println("评审后文章: " + reviewedArticle);
            });
        }
        String userText = """
                今天的微积分课程太难了!
                """;
        // 直接创建不带参数的Message对象
        Message userMessage1 = new UserMessage(userText);
        Message userMessage2 = new UserMessage("");

        Message systemMessage1 = new SystemMessage("你要以一个老师的身份回答学生的问题");

        String systemText = """
                你需要以一个非常{param}的口吻回答问题
                """;
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        Message systemMessage2 = systemPromptTemplate.createMessage(Map.of("param", "严肃"));

        // 加入的顺序也很重要
        Prompt prompt = new Prompt(List.of(userMessage1, userMessage2, systemMessage1, systemMessage2));
        String content = ChatClient.create(chatModel).prompt(prompt)
                .call()
                .content();
        log.info("content: {}", content);
    }
}