package com.example.learnai.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public AuthInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        // 白名单
        if (path.startsWith("/user/register") || path.startsWith("/user/login") ||
            path.startsWith("/weather") || path.startsWith("/deepseek") ||
            path.equals("/") || path.endsWith(".html") || path.endsWith(".js") ||
            path.endsWith(".css") || path.startsWith("/actuator") ||
            path.equals("/index.html") || path.equals("/api/env")) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
            return false;
        }

        Session session = userService.verify(auth.substring(7));
        if (session == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"登录已过期\"}");
            return false;
        }

        request.setAttribute("userId", session.getUserId());
        request.setAttribute("username", session.getUsername());
        request.setAttribute("role", session.getRole());
        request.setAttribute("isAdmin", session.getIsAdmin());
        return true;
    }
}
