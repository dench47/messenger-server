package com.messenger.messengerserver.dto;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;

public class UserDto {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean online;
    private String lastSeenText;

    public UserDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.avatarUrl = user.getAvatarUrl();
        this.online = user.getOnline() != null ? user.getOnline() : false;
        this.lastSeenText = UserService.formatLastSeenDetailed(user.getLastSeen());
    }

    // геттеры (обязательно!)
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isOnline() { return online; }
    public String getLastSeenText() { return lastSeenText; }
}