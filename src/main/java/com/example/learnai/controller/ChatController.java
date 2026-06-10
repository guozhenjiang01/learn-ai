package com.example.learnai.controller;

import com.example.learnai.agent.ChatModelFactory;
import com.example.learnai.chat.ChatHistoryService;
import com.example.learnai.chat.ChatMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @Autowired
    private ChatHistoryService historyService;

    @GetMapping("/deepSeekV4Pro")
    public String chat(@RequestParam("message") String message, HttpServletRequest req) throws IOException {
        String reply = chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt()
                .user(message)
                .call()
                .content();
        saveHistory(req, message, reply);
        return reply;
    }

    @GetMapping(value = "/deepSeekV4Pro/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam("message") String message, HttpServletRequest req) {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        // 保存用户消息
        if (userId != null) {
            try { historyService.save(new ChatMessage(userId, username, "user", message)); } catch (Exception ignored) {}
        }
        StringBuilder fullReply = new StringBuilder();
        return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt()
                .user(message)
                .stream()
                .content()
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    if (userId != null && fullReply.length() > 0) {
                        try {
                            historyService.save(new ChatMessage(userId, username, "assistant", fullReply.toString()));
                        } catch (Exception ignored) {}
                    }
                });
    }

    @GetMapping("/history")
    public List<ChatMessage> history(HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        boolean isAdmin = "admin".equals(req.getAttribute("role"));
        return historyService.list(userId, isAdmin);
    }

    private void saveHistory(HttpServletRequest req, String userMsg, String reply) throws IOException {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        if (userId != null) {
            historyService.save(new ChatMessage(userId, username, "user", userMsg));
            historyService.save(new ChatMessage(userId, username, "assistant", reply));
        }
    }
}
