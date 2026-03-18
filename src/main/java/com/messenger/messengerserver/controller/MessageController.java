package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.dto.MessageStatusBatchUpdateDto;
import com.messenger.messengerserver.dto.MessageStatusUpdateDto;
import com.messenger.messengerserver.mapper.MessageMapper;
import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.MessageStatus;
import com.messenger.messengerserver.service.FcmService;
import com.messenger.messengerserver.service.MessageService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Autowired
    private MessageService messageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private FcmService fcmService;

    @Autowired
    private MessageMapper messageMapper;

    private String getTimestamp() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    @MessageMapping("/chat")
    public void processMessage(@Payload MessageDto messageDto) {
        try {
            System.out.println("[" + getTimestamp() + "] WebSocket message received from: " + messageDto.getSenderUsername() +
                    " to: " + messageDto.getReceiverUsername());

            // 1. Сохраняем сообщение в БД со статусом SENT
            Message message = messageService.saveMessage(
                    messageDto.getContent(),
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername()
            );

            message.setStatus(MessageStatus.SENT);
            message.setIsRead(false);
            message = messageService.updateMessage(message);

            Message fullMessage = messageService.getMessageWithUsers(message.getId());
            MessageDto responseDto = messageMapper.toDto(fullMessage);

            // 2. Отправляем подтверждение ОТПРАВИТЕЛЮ (статус SENT)
            messagingTemplate.convertAndSendToUser(
                    messageDto.getSenderUsername(),
                    "/queue/messages",
                    responseDto
            );

            // 3. Проверяем, в чате ли получатель с отправителем
            boolean isReceiverInChat = userService.isUserInChatWith(
                    messageDto.getReceiverUsername(),
                    messageDto.getSenderUsername()
            );

            // 4. Пытаемся доставить ПОЛУЧАТЕЛЮ
            boolean isReceiverOnline = userService.isUserOnline(messageDto.getReceiverUsername());

            if (isReceiverOnline) {
                // Если онлайн - шлем сообщение, клиент сам ответит DELIVERED
                messagingTemplate.convertAndSendToUser(
                        messageDto.getReceiverUsername(),
                        "/queue/messages",
                        responseDto
                );
                System.out.println("[" + getTimestamp() + "] 📨 Message sent to online receiver: " + messageDto.getReceiverUsername());
            }

            // 5. FCM отправляем ВСЕГДА, КРОМЕ случая когда получатель в чате с отправителем
            if (!isReceiverInChat) {
                try {
                    fcmService.sendNewMessageNotification(
                            messageDto.getSenderUsername(),
                            messageDto.getReceiverUsername(),
                            messageDto.getContent(),
                            message.getId()
                    );
                    System.out.println("[" + getTimestamp() + "] 📱 FCM sent to receiver: " + messageDto.getReceiverUsername() +
                            " (in chat: " + isReceiverInChat + ")");
                } catch (Exception fcmEx) {
                    System.err.println("[" + getTimestamp() + "] ❌ FCM error: " + fcmEx.getMessage());
                }
            } else {
                System.out.println("[" + getTimestamp() + "] 📱 FCM skipped - receiver is in chat with sender");
            }

            System.out.println("[" + getTimestamp() + "] Message saved with status: SENT for sender: " + messageDto.getSenderUsername());

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error: " + e.getMessage());
            e.printStackTrace();

            MessageDto errorDto = new MessageDto();
            errorDto.setContent("Error sending message: " + e.getMessage());
            errorDto.setSenderUsername("system");
            errorDto.setReceiverUsername(messageDto.getSenderUsername());
            errorDto.setType("SYSTEM");

            messagingTemplate.convertAndSendToUser(
                    messageDto.getSenderUsername(),
                    "/queue/messages",
                    errorDto
            );
        }
    }

    @MessageMapping("/call")
    public void processCallSignal(@Payload Map<String, Object> callSignal) {
        try {
            System.out.println("[" + getTimestamp() + "] 📞 Call signal received: " + callSignal);

            String type = (String) callSignal.get("type");
            String from = (String) callSignal.get("from");
            String to = (String) callSignal.get("to");

            if (type == null || from == null || to == null) {
                System.err.println("[" + getTimestamp() + "] ❌ Invalid call signal format");
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    to,
                    "/queue/calls",
                    callSignal
            );

            System.out.println("[" + getTimestamp() + "] 📞 Call signal forwarded to: " + to);

            if ("offer".equals(type)) {
                try {
                    fcmService.sendIncomingCallNotification(from, to);
                    System.out.println("[" + getTimestamp() + "] 📞 FCM call notification sent to: " + to);
                } catch (Exception fcmEx) {
                    System.err.println("[" + getTimestamp() + "] ❌ Error sending call FCM: " + fcmEx.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error processing call signal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PostMapping("/send")
    public ResponseEntity<MessageDto> sendMessage(@RequestBody MessageDto messageDto) {
        try {
            Message message = messageService.saveMessage(
                    messageDto.getContent(),
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername()
            );

            message.setStatus(MessageStatus.SENT);
            message = messageService.updateMessage(message);

            MessageDto responseDto = messageMapper.toDto(message);

            System.out.println("[" + getTimestamp() + "] 🔵 [FCM CHECK] Before calling fcmService.sendNewMessageNotification");
            System.out.println("   Sender: " + messageDto.getSenderUsername());
            System.out.println("   Receiver: " + messageDto.getReceiverUsername());

            fcmService.sendNewMessageNotification(
                    messageDto.getSenderUsername(),
                    messageDto.getReceiverUsername(),
                    messageDto.getContent(),
                    message.getId()
            );

            System.out.println("[" + getTimestamp() + "] ✅ [FCM CHECK] After fcmService call");

            return ResponseEntity.ok(responseDto);

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error sending message: " + e.getMessage());
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
                    .map(messageMapper::toDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/last/{user1}/{user2}")
    public ResponseEntity<MessageDto> getLastMessage(
            @PathVariable String user1,
            @PathVariable String user2) {

        try {
            Message message = messageService.getLastMessage(user1, user2);
            if (message != null) {
                return ResponseEntity.ok(messageMapper.toDto(message));
            } else {
                return ResponseEntity.notFound().build();
            }
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
                    .map(messageMapper::toDto)
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

    @MessageMapping("/status")
    public void updateMessageStatus(@Payload MessageStatusUpdateDto statusUpdate) {
        try {
            System.out.println("[" + getTimestamp() + "] 📊 Status update received: messageId=" + statusUpdate.getMessageId() +
                    " status=" + statusUpdate.getStatus() +
                    " from=" + statusUpdate.getUsername());

            MessageDto responseDto = messageService.processStatusUpdate(statusUpdate);

            if (responseDto != null) {
                System.out.println("[" + getTimestamp() + "] 📤 Sending status to " + responseDto.getSenderUsername() +
                        " on /queue/status: " + responseDto.getStatus());

                // Пытаемся отправить через WebSocket
                messagingTemplate.convertAndSendToUser(
                        responseDto.getSenderUsername(),
                        "/queue/status",
                        responseDto
                );

                // 👇 Проверяем онлайн ли отправитель
                if (!userService.isUserOnline(responseDto.getSenderUsername())) {
                    // Если офлайн - отправляем через FCM
                    fcmService.sendDeliveredConfirmation(
                            responseDto.getSenderUsername(),
                            responseDto.getId(),
                            responseDto.getReceiverUsername()
                    );
                }

                System.out.println("[" + getTimestamp() + "] ✅ Status updated for message " + statusUpdate.getMessageId() +
                        " to " + responseDto.getStatus());
            } else {
                System.out.println("[" + getTimestamp() + "] ⚠️ No status change for message " + statusUpdate.getMessageId());
            }

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error updating message status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PostMapping("/status")
    public ResponseEntity<?> updateMessageStatusViaHttp(@RequestBody MessageStatusUpdateDto statusUpdate) {
        try {
            System.out.println("[" + getTimestamp() + "] 📊 HTTP Status update received: messageId=" + statusUpdate.getMessageId() +
                    " status=" + statusUpdate.getStatus() +
                    " from=" + statusUpdate.getUsername());

            MessageDto responseDto = messageService.processStatusUpdate(statusUpdate);

            if (responseDto != null) {
                System.out.println("[" + getTimestamp() + "] 📤 Sending status to " + responseDto.getSenderUsername() +
                        " on /queue/status: " + responseDto.getStatus());

                // 👇 Пытаемся отправить через WebSocket
                messagingTemplate.convertAndSendToUser(
                        responseDto.getSenderUsername(),
                        "/queue/status",
                        responseDto
                );

                // 👇 Проверяем онлайн ли отправитель
                if (!userService.isUserOnline(responseDto.getSenderUsername())) {
                    // Если офлайн - отправляем через FCM
                    System.out.println("[" + getTimestamp() + "] 📱 Sender offline, sending status via FCM");
                    fcmService.sendDeliveredConfirmation(
                            responseDto.getSenderUsername(),
                            responseDto.getId(),
                            responseDto.getReceiverUsername()
                    );
                }

                System.out.println("[" + getTimestamp() + "] ✅ Status updated via HTTP for message " +
                        statusUpdate.getMessageId() + " to " + responseDto.getStatus());
                return ResponseEntity.ok().build();
            } else {
                System.out.println("[" + getTimestamp() + "] ⚠️ No status change for message " + statusUpdate.getMessageId());
                return ResponseEntity.ok().build();
            }

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error updating message status via HTTP: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @MessageMapping("/status/batch")
    public void updateMessageStatusBatch(@Payload MessageStatusBatchUpdateDto batchUpdate) {
        try {
            System.out.println("[" + getTimestamp() + "] 📊 BATCH status update received: " +
                    batchUpdate.getMessageIds().size() + " messages, status=" + batchUpdate.getStatus() +
                    " from=" + batchUpdate.getUsername());

            List<MessageDto> updatedMessages = messageService.processStatusBatchUpdate(batchUpdate);

            if (!updatedMessages.isEmpty()) {
                Map<String, List<MessageDto>> bySender = updatedMessages.stream()
                        .collect(Collectors.groupingBy(MessageDto::getSenderUsername));

                for (Map.Entry<String, List<MessageDto>> entry : bySender.entrySet()) {
                    String senderUsername = entry.getKey();
                    List<MessageDto> messagesForSender = entry.getValue();

                    System.out.println("[" + getTimestamp() + "] 📤 Sending " + messagesForSender.size() +
                            " status updates to " + senderUsername + " on /queue/status");

                    messagingTemplate.convertAndSendToUser(
                            senderUsername,
                            "/queue/status",
                            messagesForSender
                    );
                }

                System.out.println("[" + getTimestamp() + "] ✅ BATCH status updated for " +
                        updatedMessages.size() + " messages");
            } else {
                System.out.println("[" + getTimestamp() + "] ⚠️ No messages updated in batch");
            }

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error processing batch status update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}