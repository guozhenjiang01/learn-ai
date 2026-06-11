package com.example.learnai.media;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
public class MediaScriptController {

    private final MediaScriptService service;

    public MediaScriptController(MediaScriptService service) {
        this.service = service;
    }

    /** 查询单条脚本 */
    @GetMapping("/scripts/{id}")
    public MediaScript getById(@PathVariable String id) throws IOException {
        MediaScript s = service.getById(id);
        if (s == null) throw new RuntimeException("not found");
        return s;
    }

    /** 列表脚本 */
    @GetMapping("/scripts")
    public List<MediaScript> list(
            @RequestParam(defaultValue = "") String topic,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        return service.list(userId, topic, status, size);
    }

    /** AI 生成脚本 */
    @PostMapping("/generate")
    public MediaScript generate(@RequestBody Map<String, String> body,
                                 HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        String topic = body.getOrDefault("topic", "日常");
        String template = body.getOrDefault("template", "其他");
        String extra = body.getOrDefault("extra", "");
        return service.generate(topic, template, extra, userId, username);
    }

    /** 更新脚本 */
    @PutMapping("/scripts/{id}")
    public MediaScript update(@PathVariable String id,
                               @RequestBody MediaScript body) throws IOException {
        body.setId(id);
        return service.update(body);
    }

    /** 删除脚本 */
    @DeleteMapping("/scripts/{id}")
    public Map<String, Object> delete(@PathVariable String id) throws IOException {
        service.delete(id);
        return Map.of("success", true);
    }
}
