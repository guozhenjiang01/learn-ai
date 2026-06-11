package com.example.learnai.terminal;

public class TerminalSession {
    private String id;
    private String userId;
    private String username;
    private String title;       // 会话标题（10字以内）
    private String content;     // 去ANSI后的纯文本
    private String rawFile;     // 原始文件路径
    private long startedAt;
    private long endedAt;
    private int lines;

    public TerminalSession() {}

    public TerminalSession(String userId, String username) {
        this.userId = userId;
        this.username = username;
        this.startedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRawFile() { return rawFile; }
    public void setRawFile(String rawFile) { this.rawFile = rawFile; }
    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
    public int getLines() { return lines; }
    public void setLines(int lines) { this.lines = lines; }
}
