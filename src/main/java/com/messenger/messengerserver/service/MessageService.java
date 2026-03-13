package com.messenger.messengerserver.service;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.dto.MessageStatusUpdateDto;
import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.MessageStatus;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService;

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

    // 👇 НОВЫЙ МЕТОД - загружает сообщение с пользователями (для WebSocket)
    @Transactional(readOnly = true)
    public Message getMessageWithUsers(Long messageId) {
        return messageRepository.findByIdWithUsers(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + messageId));
    }

    @Transactional
    public MessageDto processStatusUpdate(MessageStatusUpdateDto statusUpdate) {
        Message message = getMessageWithUsers(statusUpdate.getMessageId());

        // Проверяем, что подтверждение приходит от ПОЛУЧАТЕЛЯ
        if (!message.getReceiver().getUsername().equals(statusUpdate.getUsername())) {
            throw new RuntimeException("Unauthorized status update: " + statusUpdate.getUsername() +
                    " is not receiver of message " + statusUpdate.getMessageId());
        }

        MessageStatus newStatus = MessageStatus.valueOf(statusUpdate.getStatus());

        if (newStatus.ordinal() > message.getStatus().ordinal()) {
            message.setStatus(newStatus);
            message = updateMessage(message);

            // Создаем DTO (все данные уже загружены в транзакции)
            MessageDto dto = new MessageDto();
            dto.setId(message.getId());
            dto.setContent(message.getContent());
            dto.setTimestamp(message.getTimestamp());
            dto.setIsRead(message.getIsRead());
            dto.setSenderUsername(message.getSender().getUsername());  // Работает!
            dto.setReceiverUsername(message.getReceiver().getUsername()); // Работает!
            dto.setType(message.getType().toString());
            dto.setStatus(message.getStatus().toString());

            return dto;
        }
        return null;
    }
}