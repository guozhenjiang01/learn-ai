package com.example.learnai.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    private String id;
    private String username;
    private String password;
    private String role;       // "user" or "admin"
    private String remark;     // 超管设置的备注
    private String status;     // "active" or "disabled", null=active
    private long lastLoginAt;
    private long createdAt;

    // 永久超管名单
    public static final String[] PERMANENT_ADMINS = {"郭振江"};

    public User() {}

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isPermanentAdmin() {
        if (username == null) return false;
        for (String name : PERMANENT_ADMINS) {
            if (name.equals(username)) return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isDisabled() {
        return "disabled".equals(status);
    }

    @JsonIgnore
    public boolean isActive() {
        return status == null || "active".equals(status);
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    @JsonIgnore
    public boolean getIsAdmin() { return "admin".equals(role) || "operator".equals(role); }

    @JsonIgnore
    public boolean isSuperAdmin() { return "admin".equals(role); }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
