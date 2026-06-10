package com.example.learnai.memo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/memo")
public class MemoController {

    private final MemoService service;

    public MemoController(MemoService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public List<Memo> list(HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        boolean isAdmin = "admin".equals(req.getAttribute("role"));
        return service.list(userId, isAdmin);
    }

    @PostMapping("/add")
    public Memo add(@RequestBody Map<String, String> body, HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        String title = body.getOrDefault("title", "");
        String content = body.getOrDefault("content", "");
        return service.add(new Memo(title, content, userId, username));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) throws IOException {
        service.delete(id);
        return Map.of("success", true);
    }
}
