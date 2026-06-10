package com.example.learnai.memo;

public class Memo {
    private String id;
    private String title;
    private String content;
    private String userId;
    private String username;
    private long createdAt;

    public Memo() {}

    public Memo(String title, String content, String userId, String username) {
        this.title = title;
        this.content = content;
        this.userId = userId;
        this.username = username;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
