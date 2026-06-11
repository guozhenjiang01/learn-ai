package com.example.learnai.user;

import com.example.learnai.audit.OperationLog;
import com.example.learnai.audit.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService service;
    private final OperationLogService audit;

    public UserController(UserService service, OperationLogService audit) {
        this.service = service;
        this.audit = audit;
    }

    // ==================== 认证 ====================

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body,
                                         HttpServletRequest req) {
        try {
            User user = service.register(body.get("username"), body.get("password"));
            auditLog(user.getId(), user.getUsername(), "REGISTER", "新用户注册", req);
            return Map.of("success", true, "user", user);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                      HttpServletRequest req) {
        try {
            Session session = service.login(body.get("username"), body.get("password"));
            auditLog(session.getUserId(), session.getUsername(), "LOGIN", "登录成功", req);
            return Map.of("success", true, "token", session.getToken(),
                    "username", session.getUsername(), "role", session.getRole(),
                    "isAdmin", session.getIsAdmin());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader("Authorization") String auth,
                                       HttpServletRequest req) {
        try {
            String token = auth.replace("Bearer ", "");
            String username = (String) req.getAttribute("username");
            String userId = (String) req.getAttribute("userId");
            service.logout(token);
            if (username != null) {
                auditLog(userId, username, "LOGOUT", "退出登录", req);
            }
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
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            return Map.of("success", true, "users", service.listUsers());
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 搜索用户 */
    @GetMapping("/search")
    public Map<String, Object> searchUsers(@RequestParam String keyword, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            return Map.of("success", true, "users", service.searchUsers(keyword));
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 用户统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats(HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
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
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            service.promoteToAdmin(userId);
            auditLog(userId, "目标用户", "PROMOTE_ADMIN", "提升为管理员", req);
            return Map.of("success", true, "message", "已提升为管理员");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 降级为普通用户（永久超管不可降级） */
    @PostMapping("/demote/{userId}")
    public Map<String, Object> demote(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            service.demoteFromAdmin(userId);
            auditLog(userId, "目标用户", "DEMOTE_USER", "降为普通用户", req);
            return Map.of("success", true, "message", "已降为普通用户");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 禁用用户（永久超管不可禁用） */
    @PostMapping("/disable/{userId}")
    public Map<String, Object> disable(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            service.disableUser(userId);
            auditLog(userId, "目标用户", "DISABLE_USER", "禁用账号", req);
            return Map.of("success", true, "message", "已禁用");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 启用用户 */
    @PostMapping("/enable/{userId}")
    public Map<String, Object> enable(@PathVariable String userId, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            service.enableUser(userId);
            auditLog(userId, "目标用户", "ENABLE_USER", "启用账号", req);
            return Map.of("success", true, "message", "已启用");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 重置用户密码 */
    @PostMapping("/reset-password/{userId}")
    public Map<String, Object> resetPassword(@PathVariable String userId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
        try {
            service.resetPassword(userId, body.get("password"));
            auditLog(userId, "目标用户", "RESET_PWD", "重置密码", req);
            return Map.of("success", true, "message", "密码已重置");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /** 设置用户备注 */
    @PostMapping("/remark/{userId}")
    public Map<String, Object> setRemark(@PathVariable String userId, @RequestBody Map<String, String> body, HttpServletRequest req) {
        if (!isAdmin(req)) return Map.of("success", false, "message", "权限不足");
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

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        return ip != null ? ip.split(",")[0].trim() : req.getRemoteAddr();
    }

    private void auditLog(String userId, String username, String action,
                          String detail, HttpServletRequest req) {
        try {
            audit.log(new OperationLog(userId, username, action, detail, clientIp(req)));
        } catch (Exception ignored) {}
    }
}
