package com.example.learnai.hermes;

import com.example.learnai.terminal.TerminalSession;
import com.example.learnai.terminal.TerminalSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hermes")
public class HermesController {

    @Autowired
    private HermesService hermesService;

    @Autowired
    private TerminalSessionService terminalSessionService;

    /** 检查是否为管理员，否则返回 403 */
    private boolean requireAdmin(HttpServletRequest req, Object response) {
        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) {
            return false;
        }
        return true;
    }

    /** 非流式对话 */
    @PostMapping("/chat")
    public HermesService.HermesResult chat(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!requireAdmin(req, null)) {
            HermesService.HermesResult r = new HermesService.HermesResult();
            r.setSuccess(false);
            r.setError("仅超级管理员可使用终端功能");
            return r;
        }
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            HermesService.HermesResult r = new HermesService.HermesResult();
            r.setSuccess(false);
            r.setError("消息不能为空");
            return r;
        }
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        if (userId == null) userId = "anon";
        return hermesService.chat(userId, username, message);
    }

    /** 真正的流式对话：逐行 SSE 推送 */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!requireAdmin(req, null)) {
            return Flux.just("data:❌ 仅超级管理员可使用终端功能\n\n");
        }
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Flux.just("data:错误：消息不能为空\n\n");
        }
        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        if (userId == null) userId = "anon";

        return hermesService.chatStream(userId, username, message)
                .map(line -> "data:" + line + "\n\n");
    }

    /** 清除当前用户的对话历史 */
    @PostMapping("/reset")
    public Map<String, Object> reset(HttpServletRequest req) {
        if (!requireAdmin(req, null)) {
            return Map.of("success", false, "message", "仅超级管理员可使用终端功能");
        }
        String userId = (String) req.getAttribute("userId");
        if (userId == null) userId = "anon";
        hermesService.clearSession(userId);
        return Map.of("success", true, "message", "对话已重置");
    }

    /** 获取对话历史 */
    @GetMapping("/history")
    public List<HermesService.Turn> history(HttpServletRequest req) {
        if (!requireAdmin(req, null)) {
            return List.of();
        }
        String userId = (String) req.getAttribute("userId");
        if (userId == null) userId = "anon";
        return hermesService.getHistory(userId);
    }

    /** 兼容旧接口 */
    @PostMapping("/execute")
    public HermesService.HermesResult execute(@RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!requireAdmin(req, null)) {
            HermesService.HermesResult r = new HermesService.HermesResult();
            r.setSuccess(false);
            r.setError("仅超级管理员可使用终端功能");
            return r;
        }
        String message = body.getOrDefault("command", body.get("message"));
        if (message == null || message.isBlank()) {
            HermesService.HermesResult r = new HermesService.HermesResult();
            r.setSuccess(false);
            r.setError("命令不能为空");
            return r;
        }
        String username = (String) req.getAttribute("username");
        return hermesService.chat("_oneshot_" + System.currentTimeMillis(), username, message);
    }

    // ==================== 终端录制回放 ====================

    /** 终端会话列表 */
    @GetMapping("/terminal-sessions")
    public Map<String, Object> terminalSessions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long fromTs,
            @RequestParam(required = false) Long toTs,
            HttpServletRequest req) {
        String role = (String) req.getAttribute("role");
        String selfId = (String) req.getAttribute("userId");
        boolean isAdmin = "admin".equals(role);
        // 超管看全部，普通用户只能看自己的
        if (!isAdmin) {
            userId = selfId;
            fromTs = null; toTs = null;
        }
        try {
            List<TerminalSession> sessions = terminalSessionService.list(
                userId, fromTs, toTs, 5);
            return Map.of("success", true, "sessions", sessions);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 终端会话详情 */
    @GetMapping("/terminal-sessions/{id}")
    public Map<String, Object> terminalSession(@PathVariable String id, HttpServletRequest req) {
        try {
            TerminalSession s = terminalSessionService.getById(id);
            if (s == null) return Map.of("success", false, "message", "不存在");
            return Map.of("success", true, "session", s);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}
