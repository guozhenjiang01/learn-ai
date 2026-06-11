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

    /** 通勤素材包生成（支持勾选 topics） */
    @SuppressWarnings("unchecked")
    @PostMapping("/commute-pack")
    public Map<String, Object> commutePack(@RequestBody Map<String, Object> body,
                                            HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        List<String> topics = (List<String>) body.get("topics");
        String extra = (String) body.getOrDefault("extra", "");
        String weather = (String) body.getOrDefault("weather", "");

        String pack = service.generateCommutePack(topics, extra, weather);
        return Map.of("pack", pack);
    }

    /** 评审后修改 */
    @PostMapping("/revise")
    public Map<String, Object> revise(@RequestBody Map<String, String> body) throws IOException {
        String previous = body.getOrDefault("previous", "");
        String review = body.getOrDefault("review", "");

        String revised = service.revisePack(previous, review);
        return Map.of("pack", revised);
    }

    /** 采纳 → 存入素材库 */
    @PostMapping("/materials")
    public MediaScript adopt(@RequestBody Map<String, String> body,
                              HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        MediaScript s = new MediaScript();
        s.setUserId(userId);
        s.setUsername(username);
        s.setTitle(body.getOrDefault("title", "通勤素材"));
        s.setTopic(body.getOrDefault("topic", "通勤"));
        s.setTemplate(body.getOrDefault("template", ""));
        s.setContent(body.getOrDefault("content", ""));
        s.setStatus("adopted");
        return service.create(s);
    }

    /** 素材库列表（支持按类目筛选） */
    @GetMapping("/materials")
    public List<MediaScript> materials(
            @RequestParam(defaultValue = "") String topic,
            HttpServletRequest req) throws IOException {
        String userId = (String) req.getAttribute("userId");
        return service.list(userId, topic, "adopted", 100);
    }

    /** 删除素材 */
    @DeleteMapping("/materials/{id}")
    public Map<String, Object> deleteMaterial(@PathVariable String id) throws IOException {
        service.delete(id);
        return Map.of("success", true);
    }

    /** 查询单条 */
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
}
