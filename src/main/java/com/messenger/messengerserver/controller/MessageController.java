package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.FcmService;
import com.messenger.messengerserver.service.MessageService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private FcmService fcmService; // Добавить

    // WebSocket endpoint для отправки сообщений в реальном времени
    @MessageMapping("/chat")
    public void processMessage(@Payload MessageDto messageDto) {
        try {
            System.out.println("WebSocket message received from: " + messageDto.getSenderUsername() + " to: " + messageDto.getReceiverUsername());

            // Сохраняем сообщение в базу
            Message message = messageService.sendMessage(
                    messageDto.getContent(),
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername()
            );

            MessageDto responseDto = convertToDto(message);

            // +++ ДОБАВЬ ЭТОТ КОД: ОТПРАВКА FCM +++
            System.out.println("🔵 [FCM WS] Before FCM call in WebSocket");
            System.out.println("   Sender: " + messageDto.getSenderUsername());
            System.out.println("   Receiver: " + messageDto.getReceiverUsername());

            // Отправляем FCM уведомление
            try {
                fcmService.sendNewMessageNotification(
                        messageDto.getSenderUsername(),
                        messageDto.getReceiverUsername(),
                        messageDto.getContent()
                );
                System.out.println("✅ [FCM WS] FCM sent successfully via WebSocket");
            } catch (Exception fcmEx) {
                System.err.println("❌ [FCM WS] Error sending FCM: " + fcmEx.getMessage());
                fcmEx.printStackTrace();
            }
            // +++ КОНЕЦ ДОБАВЛЕННОГО КОДА +++

            // Отправляем сообщение ПОЛУЧАТЕЛЮ
            messagingTemplate.convertAndSendToUser(
                    messageDto.getReceiverUsername(),
                    "/queue/messages",
                    responseDto
            );

            // Отправляем сообщение ОТПРАВИТЕЛЮ
            messagingTemplate.convertAndSendToUser(
                    messageDto.getSenderUsername(),
                    "/queue/messages",
                    responseDto
            );

            System.out.println("Message sent via WebSocket to both users");

        } catch (Exception e) {
            System.out.println("Error sending message via WebSocket: " + e.getMessage());
            e.printStackTrace();

            // Отправляем ошибку только отправителю
            MessageDto errorDto = new MessageDto();
            errorDto.setContent("Error sending message: " + e.getMessage());
            errorDto.setSenderUsername("system");
            errorDto.setReceiverUsername(messageDto.getSenderUsername());

            messagingTemplate.convertAndSendToUser(
                    messageDto.getSenderUsername(),
                    "/queue/messages",
                    errorDto
            );
        }
    }

    // REST endpoint для отправки сообщений (для обратной совместимости)
    @PostMapping("/send")
    public ResponseEntity<MessageDto> sendMessage(@RequestBody MessageDto messageDto) {
        try {
            Message message = messageService.sendMessage(
                    messageDto.getContent(),
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername()
            );

            MessageDto responseDto = convertToDto(message);

            // +++ ДОБАВЬТЕ ЭТОТ ЛОГ +++
            System.out.println("🔵 [FCM CHECK] Before calling fcmService.sendNewMessageNotification");
            System.out.println("   Sender: " + messageDto.getSenderUsername());
            System.out.println("   Receiver: " + messageDto.getReceiverUsername());
            System.out.println("   fcmService is null? " + (fcmService == null));

            // Вызов FCM
            fcmService.sendNewMessageNotification(
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername(),
                    messageDto.getContent()
            );

            System.out.println("✅ [FCM CHECK] After fcmService call");

            return ResponseEntity.ok(responseDto);

        } catch (Exception e) {
            System.err.println("❌ Error sending message: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/conversation")
    public ResponseEntity<List<MessageDto>> getConversation(
            @RequestParam String user1,
            @RequestParam String user2) {

        try {
            List<Message> messages = messageService.getConversation(user1, user2);
            List<MessageDto> messageDtos = messages.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long messageId) {
        try {
            messageService.markAsRead(messageId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error marking message as read");
        }
    }

    @GetMapping("/unread")
    public ResponseEntity<List<MessageDto>> getUnreadMessages(@RequestParam String username) {
        try {
            List<Message> messages = messageService.getUnreadMessages(username);
            List<MessageDto> messageDtos = messages.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Long> getUnreadCount(@RequestParam String username) {
        try {
            long count = messageService.getUnreadCount(username);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp());
        dto.setIsRead(message.getIsRead());
        dto.setSenderUsername(message.getSender().getUsername());
        dto.setReceiverUsername(message.getReceiver().getUsername());
        dto.setType(message.getType().toString());
        return dto;
    }
}