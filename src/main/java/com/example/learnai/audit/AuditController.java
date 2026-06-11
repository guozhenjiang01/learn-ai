package com.example.learnai.audit;

import com.example.learnai.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final OperationLogService logService;
    private final UserService userService;

    public AuditController(OperationLogService logService, UserService userService) {
        this.logService = logService;
        this.userService = userService;
    }

    /** 查询操作日志（仅 admin 角色） */
    @GetMapping("/logs")
    public Map<String, Object> queryLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long fromTs,
            @RequestParam(required = false) Long toTs,
            @RequestParam(defaultValue = "100") int size,
            HttpServletRequest req) throws IOException {

        String role = (String) req.getAttribute("role");
        if (!"admin".equals(role)) {
            return Map.of("success", false, "message", "仅超管可查看");
        }

        List<OperationLog> logs = logService.query(userId, action, fromTs, toTs, size);
        return Map.of("success", true, "logs", logs);
    }

    /** 记录操作（前端调用） */
    @PostMapping("/record")
    public Map<String, Object> record(@RequestBody Map<String, String> body,
                                       HttpServletRequest req) {
        String action = body.get("action");
        String detail = body.getOrDefault("detail", "");

        String userId = (String) req.getAttribute("userId");
        String username = (String) req.getAttribute("username");
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null) ip = req.getRemoteAddr();

        // 未登录用户用 session id
        if (userId == null) {
            userId = req.getSession().getId();
            username = body.getOrDefault("username", "匿名");
        }

        logService.log(new OperationLog(userId, username, action, detail, ip));
        return Map.of("success", true);
    }
}
