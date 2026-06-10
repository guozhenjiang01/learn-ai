package com.example.learnai.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // ==================== 认证 ====================

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        try {
            User user = service.register(body.get("username"), body.get("password"));
            return Map.of("success", true, "user", user);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        try {
            Session session = service.login(body.get("username"), body.get("password"));
            return Map.of("success", true, "token", session.getToken(),
                    "username", session.getUsername(), "role", session.getRole(),
                    "isAdmin", session.getIsAdmin());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader("Authorization") String auth) {
        try {
            String token = auth.replace("Bearer ", "");
            service.logout(token);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("Authorization") String auth) {
        try {
            String token = auth.replace("Bearer ", "");
            Session session = service.verify(token);
            if (session == null) return Map.of("success", false, "message", "未登录");
            return Map.of("success", true, "username", session.getUsername(), "role", session.getRole(),
                    "userId", session.getUserId(), "isAdmin", session.getIsAdmin());
        } catch (IOException e) {
            return Map.of("success", false, "message", "验证失败");
        }
    }

    // ==================== 用户管理（仅超管） ====================

    /** 用户列表 */
    @GetMapping("/list")
    public Map<String, Object> listUsers(HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            return Map.of("success", true, "users", service.listUsers());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 搜索用户 */
    @GetMapping("/search")
    public Map<String, Object> searchUsers(@RequestParam String keyword, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            return Map.of("success", true, "users", service.searchUsers(keyword));
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 用户统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            Map<String, Long> s = service.getUserStats();
            return Map.of("success", true, "stats", s);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 提升为超管 */
    @PostMapping("/promote/{userId}")
    public Map<String, Object> promote(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.promoteToAdmin(userId);
            return Map.of("success", true, "message", "已提升为超管");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 降级为普通用户（永久超管不可降级） */
    @PostMapping("/demote/{userId}")
    public Map<String, Object> demote(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.demoteFromAdmin(userId);
            return Map.of("success", true, "message", "已降为普通用户");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 禁用用户（永久超管不可禁用） */
    @PostMapping("/disable/{userId}")
    public Map<String, Object> disable(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.disableUser(userId);
            return Map.of("success", true, "message", "已禁用");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 启用用户 */
    @PostMapping("/enable/{userId}")
    public Map<String, Object> enable(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.enableUser(userId);
            return Map.of("success", true, "message", "已启用");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 重置用户密码 */
    @PostMapping("/reset-password/{userId}")
    public Map<String, Object> resetPassword(@PathVariable String userId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.resetPassword(userId, body.get("password"));
            return Map.of("success", true, "message", "密码已重置");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 设置用户备注 */
    @PostMapping("/remark/{userId}")
    public Map<String, Object> setRemark(@PathVariable String userId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "仅超管可操作");
        try {
            service.setRemark(userId, body.get("remark"));
            return Map.of("success", true, "message", "备注已更新");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private boolean isAdmin(HttpServletRequest req) {
        Boolean isAdmin = (Boolean) req.getAttribute("isAdmin");
        return isAdmin != null && isAdmin;
    }
}
