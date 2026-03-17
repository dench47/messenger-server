package com.messenger.messengerserver.dto;

import java.util.List;

public class MessageStatusBatchUpdateDto {
    private List<Long> messageIds;
    private String status; // "DELIVERED" или "READ"
    private String username; // кто подтверждает (получатель)

    public MessageStatusBatchUpdateDto() {}

    public MessageStatusBatchUpdateDto(List<Long> messageIds, String status, String username) {
        this.messageIds = messageIds;
        this.status = status;
        this.username = username;
    }

    // Getters and Setters
    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
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
        return "MessageStatusBatchUpdateDto{" +
                "messageIds=" + messageIds +
                ", status='" + status + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}