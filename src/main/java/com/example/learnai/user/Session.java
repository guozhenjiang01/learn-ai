package com.example.learnai.user;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Session {
    private String token;
    private String userId;
    private String username;
    private String role;
    private long createdAt;
    private long expiresAt;

    public Session() {}

    public Session(String token, String userId, String username, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + 7 * 24 * 3600 * 1000L;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean getIsAdmin() { return "admin".equals(role); }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    @JsonIgnore
    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
}
