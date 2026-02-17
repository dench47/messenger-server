package com.messenger.messengerserver.dto;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;

public class ContactDto {
    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean online;
    private String status;        // "online" или "offline"
    private String lastSeenText;   // "online" или "Был в 15:30"

    public ContactDto(User contact) {
        this.id = contact.getId();
        this.username = contact.getUsername();
        this.displayName = contact.getDisplayName();
        this.avatarUrl = contact.getAvatarUrl();

        // Статус из БД/Redis
        this.online = contact.getOnline() != null ? contact.getOnline() : false;

        // Формируем текстовое представление
        if (this.online) {
            this.status = "online";
            this.lastSeenText = "online";
        } else {
            this.status = "offline";
            this.lastSeenText = UserService.formatLastSeenDetailed(contact.getLastSeen());
        }
    }

    // Геттеры (обязательно!)
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isOnline() { return online; }
    public String getStatus() { return status; }
    public String getLastSeenText() { return lastSeenText; }
}