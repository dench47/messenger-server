package com.messenger.messengerserver.dto;

import java.time.LocalDateTime;

public class MessageDto {
    private Long id;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isRead;
    private String senderUsername;
    private String receiverUsername;
    private String type;
    // 👇 НОВОЕ ПОЛЕ
    private String status; // "SENT", "DELIVERED", "READ"

    // Конструкторы
    public MessageDto() {}

    public MessageDto(Long id, String content, LocalDateTime timestamp, Boolean isRead,
                      String senderUsername, String receiverUsername, String type, String status) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.type = type;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getReceiverUsername() { return receiverUsername; }
    public void setReceiverUsername(String receiverUsername) { this.receiverUsername = receiverUsername; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // 👇 NEW getter/setter
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}