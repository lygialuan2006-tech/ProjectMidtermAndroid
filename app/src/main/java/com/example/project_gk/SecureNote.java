package com.example.project_gk;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "secure_notes")
public class SecureNote {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String title;
    private String category;
    private String secretContent;
    private long timestamp;

    public SecureNote(String title, String category, String secretContent) {
        this.title = title;
        this.category = category;
        this.secretContent = secretContent;
        this.timestamp = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSecretContent() { return secretContent; }
    public void setSecretContent(String secretContent) { this.secretContent = secretContent; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}