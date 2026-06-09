package com.example.learnai.controller;

import com.example.learnai.agent.ChatModelFactory;
import com.example.learnai.rag.LocalDocumentStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 接口：基于本地文档检索增强生成
 *
 * @author guozhenjiang9
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    @Autowired
    private LocalDocumentStore documentStore;

    @Autowired
    private ChatModelFactory chatModelFactory;

    @GetMapping("/chat")
    public String chat(@RequestParam("message") String message) {
        // 1. 检索相关文档段落
        List<String> relevantDocs = documentStore.search(message, 3);
        String context = relevantDocs.stream().collect(Collectors.joining("\n\n---\n\n"));

        // 2. 构造带上下文的 prompt
        String prompt = """
                你是一个技术文档助手，请根据以下参考文档内容回答用户的问题。
                如果文档中没有相关信息，请如实告知。
                
                【参考文档】
                %s
                
                【用户问题】
                %s
                """.formatted(context, message);

        // 3. 调用模型生成回答
        return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}