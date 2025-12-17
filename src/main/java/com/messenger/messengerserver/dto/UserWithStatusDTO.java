package com.messenger.messengerserver.dto;

import com.messenger.messengerserver.model.User;
import java.time.LocalDateTime;
import java.time.Duration;

public class UserWithStatusDTO {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String status; // "online", "active", "offline", "inactive"
    private String lastSeenText; // "2 minutes ago", "just now"

    public UserWithStatusDTO() {}

    public UserWithStatusDTO(Long id, String username, String displayName,
                             String avatarUrl, String status, String lastSeenText) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.lastSeenText = lastSeenText;
    }








    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLastSeenText() { return lastSeenText; }
    public void setLastSeenText(String lastSeenText) { this.lastSeenText = lastSeenText; }
}