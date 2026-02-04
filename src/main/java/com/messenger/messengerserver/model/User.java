package com.messenger.messengerserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "fcm_token")
    private String fcmToken;

    private String displayName;
    private String avatarUrl;

    private Boolean online = false;

    private LocalDateTime lastSeen;

    private LocalDateTime createdAt;

    private LocalDateTime lastActivity; // оставляем для статистики

    // Конструкторы
    public User() {
        this.createdAt = LocalDateTime.now();
        // УБИРАЕМ автоустановку lastActivity
    }

    public User(String username, String password) {
        this();
        this.username = username;
        this.password = password;
        this.displayName = username;
    }

    // Геттеры и сеттеры
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) {
        this.online = online;
        // При установке офлайн сразу ставим last seen
        if (online != null && !online) {
            this.lastSeen = LocalDateTime.now();
        }
    }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // УДАЛЯЕМ @PreUpdate - логика проще:
    // lastSeen устанавливается только когда пользователь отключается
}