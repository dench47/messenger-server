package com.messenger.messengerserver.service;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.dto.MessageStatusBatchUpdateDto;
import com.messenger.messengerserver.dto.MessageStatusUpdateDto;
import com.messenger.messengerserver.mapper.MessageMapper;
import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.MessageStatus;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageMapper messageMapper;

    @Transactional
    public Message saveMessage(String content, String senderUsername, String receiverUsername) {
        User sender = userService.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userService.findByUsername(receiverUsername)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        Message message = new Message(content, sender, receiver);
        return messageRepository.save(message);
    }

    public List<Message> getConversation(String user1, String user2) {
        return messageRepository.findConversationByUsernames(user1, user2);
    }

    @Transactional
    public void markAsRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.setIsRead(true);
        messageRepository.save(message);
    }

    public List<Message> getUnreadMessages(String username) {
        return messageRepository.findUnreadMessagesByUsername(username);
    }

    public long getUnreadCount(String username) {
        return messageRepository.findUnreadMessagesByUsername(username).size();
    }

    public Message getLastMessage(String user1, String user2) {
        return messageRepository.findLastMessageBetweenUsers(user1, user2);
    }

    public Message updateMessage(Message message) {
        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Message getMessageWithUsers(Long messageId) {
        return messageRepository.findByIdWithUsers(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + messageId));
    }

    @Transactional
    public MessageDto processStatusUpdate(MessageStatusUpdateDto statusUpdate) {
        Message message = getMessageWithUsers(statusUpdate.getMessageId());

        if (!message.getReceiver().getUsername().equals(statusUpdate.getUsername())) {
            throw new RuntimeException("Unauthorized status update: " + statusUpdate.getUsername() +
                    " is not receiver of message " + statusUpdate.getMessageId());
        }

        MessageStatus newStatus = MessageStatus.valueOf(statusUpdate.getStatus());

        if (newStatus.ordinal() > message.getStatus().ordinal()) {
            message.setStatus(newStatus);

            if (newStatus == MessageStatus.READ) {
                message.setIsRead(true);
            }

            message = updateMessage(message);

            return messageMapper.toDto(message);
        }
        return null;
    }

    @Transactional
    public List<MessageDto> processStatusBatchUpdate(MessageStatusBatchUpdateDto batchUpdate) {
        List<Long> messageIds = batchUpdate.getMessageIds();
        String statusStr = batchUpdate.getStatus();
        String username = batchUpdate.getUsername();

        MessageStatus newStatus = MessageStatus.valueOf(statusStr);
        List<MessageDto> updatedMessages = new ArrayList<>();

        for (Long messageId : messageIds) {
            try {
                Message message = getMessageWithUsers(messageId);

                if (!message.getReceiver().getUsername().equals(username)) {
                    System.err.println("⚠️ Unauthorized status update for message " + messageId +
                            ": " + username + " is not receiver");
                    continue;
                }

                if (newStatus.ordinal() > message.getStatus().ordinal()) {
                    message.setStatus(newStatus);

                    if (newStatus == MessageStatus.READ) {
                        message.setIsRead(true);
                    }

                    message = updateMessage(message);

                    updatedMessages.add(messageMapper.toDto(message));
                }
            } catch (Exception e) {
                System.err.println("❌ Error processing message " + messageId + ": " + e.getMessage());
            }
        }

        return updatedMessages;
    }

    public List<Message> getUndeliveredMessages(String username) {
        return messageRepository.findByReceiverUsernameAndStatus(username, MessageStatus.SENT);
    }
}