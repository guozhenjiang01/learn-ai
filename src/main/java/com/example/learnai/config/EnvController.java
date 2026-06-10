package com.example.learnai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EnvController {

    @Value("${spring.profiles.active:staging}")
    private String activeProfile;

    @Value("${server.port:8080}")
    private int serverPort;

    @GetMapping("/api/env")
    public Map<String, Object> env() {
        return Map.of(
            "profile", activeProfile,
            "port", serverPort
        );
    }
}
