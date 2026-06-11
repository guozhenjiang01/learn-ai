package com.example.learnai.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaCategory {
    private String id;
    private String name;   // 类目名，如"通勤"
    private String emoji;  // 图标，如"🚄"
    private int sort;      // 排序

    public MediaCategory() {}
    public MediaCategory(String name, String emoji, int sort) {
        this.name = name; this.emoji = emoji; this.sort = sort;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }
}
