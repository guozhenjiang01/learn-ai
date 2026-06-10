package com.example.learnai.house;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/house")
public class HouseNewsController {

    private static final Path CACHE = Paths.get("/tmp/house-news-cache.json");

    @GetMapping("/news")
    public String news() throws IOException {
        if (Files.exists(CACHE)) {
            return Files.readString(CACHE);
        }
        return "[]";
    }
}
