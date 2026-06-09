package com.example.learnai.controller;

import com.example.learnai.agent.ChatModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author guozhenjiang9
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatModelFactory chatModelFactory;

    @GetMapping("/deepSeekV4Pro")
    public String chat(@RequestParam("message") String message) {
        return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping(value = "/deepSeekV4Pro/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam("message") String message) {
        return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt()
                .user(message)
                .stream()
                .content();
    }
}