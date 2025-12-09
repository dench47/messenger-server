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

    public UserWithStatusDTO(User user, boolean isActuallyOnline) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.avatarUrl = user.getAvatarUrl();
        this.status = calculateStatus(user, isActuallyOnline);
        this.lastSeenText = formatLastSeen(user.getLastSeen());
    }

    public UserWithStatusDTO(User user, boolean hasWebSocket, boolean isActuallyActive) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.avatarUrl = user.getAvatarUrl();
        this.status = calculateStatus(hasWebSocket, isActuallyActive);
        this.lastSeenText = formatLastSeen(user.getLastSeen());
    }

    private String calculateStatus(boolean hasWebSocket, boolean isActuallyActive) {
        // ДЕБАГ вывод
        System.out.println("DEBUG: hasWebSocket=" + hasWebSocket + ", isActuallyActive=" + isActuallyActive);

        if (!hasWebSocket) {
            return "offline";
        }

        if (isActuallyActive) {
            return "active";
        } else {
            return "inactive"; // ← ДОЛЖЕН ВЫЗЫВАТЬСЯ!
        }
    }

    private String calculateStatus(User user, boolean isActuallyOnline) {
        if (!isActuallyOnline) {
            return "offline";
        }

        if (user.getLastActivity() != null) {
            LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
            if (user.getLastActivity().isAfter(twoMinutesAgo)) {
                return "active";
            } else {
                return "inactive";
            }
        }

        return "online";
    }

    private String formatLastSeen(LocalDateTime lastSeen) {
        if (lastSeen == null) return "never";

        Duration duration = Duration.between(lastSeen, LocalDateTime.now());
        long minutes = duration.toMinutes();

        if (minutes < 1) return "just now";
        if (minutes == 1) return "1 minute ago";
        if (minutes < 60) return minutes + " minutes ago";

        long hours = duration.toHours();
        if (hours == 1) return "1 hour ago";
        if (hours < 24) return hours + " hours ago";

        long days = duration.toDays();
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";

        return "long time ago";
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