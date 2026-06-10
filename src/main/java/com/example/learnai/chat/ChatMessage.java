package com.example.learnai.chat;

public class ChatMessage {
    private String id;
    private String userId;
    private String username;
    private String role; // "user" or "assistant"
    private String content;
    private long createdAt;

    public ChatMessage() {}

    public ChatMessage(String userId, String username, String role, String content) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
