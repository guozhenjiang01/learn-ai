package com.example.learnai.usage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/deepseek")
public class DeepSeekUsageController {

    @Value("${deepseek.api-key}")
    private String apiKey;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @GetMapping("/balance")
    public String balance() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/user/balance"))
                .header("Authorization", "Bearer " + apiKey)
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
