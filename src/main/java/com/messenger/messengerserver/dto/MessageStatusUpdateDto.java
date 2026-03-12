package com.messenger.messengerserver.dto;

public class MessageStatusUpdateDto {
    private Long messageId;
    private String status; // "DELIVERED" или "READ"
    private String username; // кто подтверждает (получатель)

    public MessageStatusUpdateDto() {}

    public MessageStatusUpdateDto(Long messageId, String status, String username) {
        this.messageId = messageId;
        this.status = status;
        this.username = username;
    }

    // Getters and Setters
    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "MessageStatusUpdateDto{" +
                "messageId=" + messageId +
                ", status='" + status + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}