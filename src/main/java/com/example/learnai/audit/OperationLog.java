package com.example.learnai.audit;

public class OperationLog {
    private String id;
    private String userId;
    private String username;
    private String action;      // LOGIN, REGISTER, LOGOUT, VIEW_PAGE, VIEW_WEATHER, etc.
    private String detail;      // e.g. "/index.html", "北京+榆次"
    private String ip;
    private long timestamp;

    public OperationLog() {}

    public OperationLog(String userId, String username, String action, String detail, String ip) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.detail = detail;
        this.ip = ip;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
