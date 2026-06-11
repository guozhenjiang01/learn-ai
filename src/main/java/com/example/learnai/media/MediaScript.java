package com.example.learnai.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaScript {
    private String id;
    private String userId;
    private String username;
    private String title;        // 脚本标题
    private String topic;        // 选题：通勤/工资/老婆/带娃/大厂/其他
    private String template;     // 模版：周一通勤/工资到账/北大老婆/周五回家/其他
    private String content;      // 完整脚本（含分镜）
    private String platform;     // 平台：抖音
    private String status;       // 状态：draft/scheduled/published
    private Long scheduledAt;    // 计划发布时间
    private Long publishedAt;    // 实际发布时间
    private String publishUrl;   // 发布链接
    private long createdAt;
    private long updatedAt;

    public MediaScript() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Long scheduledAt) { this.scheduledAt = scheduledAt; }
    public Long getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }
    public String getPublishUrl() { return publishUrl; }
    public void setPublishUrl(String publishUrl) { this.publishUrl = publishUrl; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
