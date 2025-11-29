package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserService userService;

    public Message sendMessage(String content, String senderUsername, String receiverUsername) {
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
}