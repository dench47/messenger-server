package com.messenger.messengerserver.config;

import com.messenger.messengerserver.dto.MessageDto;
import com.messenger.messengerserver.mapper.MessageMapper;
import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.service.MessageService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageMapper messageMapper;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = null;
        if (headerAccessor.getUser() != null) {
            username = headerAccessor.getUser().getName();
        }

        if (username == null) {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes != null) {
                username = (String) sessionAttributes.get("username");
            }
        }

        String internalSessionId = headerAccessor.getSessionId();

        // Получаем RabbitMQ sessionId из сообщения CONNECTED
        String rabbitSessionId = null;
        try {
            // Парсим напрямую из native заголовков
            Object nativeHeadersObj = headerAccessor.getHeader("nativeHeaders");
            if (nativeHeadersObj instanceof Map) {
                Map<String, List<String>> nativeHeaders = (Map<String, List<String>>) nativeHeadersObj;
                List<String> sessionValues = nativeHeaders.get("session");
                if (sessionValues != null && !sessionValues.isEmpty()) {
                    rabbitSessionId = sessionValues.get(0);
                    System.out.println("[SESSION] ✅ Получен RabbitMQ sessionId из nativeHeaders: " + rabbitSessionId);
                }
            }
        } catch (Exception e) {
            System.err.println("[SESSION] ❌ Ошибка получения RabbitMQ sessionId: " + e.getMessage());
        }

        // Если не получили - используем internalSessionId
        if (rabbitSessionId == null) {
            rabbitSessionId = internalSessionId;
            System.out.println("[SESSION] ⚠️ Используем internalSessionId как rabbitSessionId: " + rabbitSessionId);
        }

        if (username != null) {
            userService.userConnected(username, internalSessionId, rabbitSessionId);
            broadcastOnlineUsers();
            sendPersonalOnlineUsers(username);
            sendUndeliveredMessages(username);

            System.out.println("✅ User CONNECTED: " + username +
                    " (internalSession: " + internalSessionId + ", rabbitSession: " + rabbitSessionId + ")");
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = null;
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            username = (String) sessionAttributes.get("username");
        }

        if (username == null && headerAccessor.getUser() != null) {
            username = headerAccessor.getUser().getName();
        }

        String internalSessionId = headerAccessor.getSessionId();

        if (username != null) {
            userService.userDisconnected(username, internalSessionId);
            broadcastOnlineUsers();
            System.out.println("🔴 User DISCONNECTED: " + username +
                    " (internalSession: " + internalSessionId + ")");
        }
    }

    private void broadcastOnlineUsers() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            messagingTemplate.convertAndSend("/topic/online.users", onlineUsers);
            System.out.println("📡 [BROADCAST] Online users: " + onlineUsers.size() + " users");
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting online users: " + e.getMessage());
        }
    }

    private void sendPersonalOnlineUsers(String username) {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            messagingTemplate.convertAndSendToUser(username, "/queue/online.users", onlineUsers);
            System.out.println("📡 [PERSONAL] Sent online users to " + username +
                    ": " + onlineUsers.size() + " users");
        } catch (Exception e) {
            System.err.println("❌ Error sending personal online users to " + username +
                    ": " + e.getMessage());
        }
    }

    private void sendUndeliveredMessages(String username) {
        try {
            List<Message> undeliveredMessages = messageService.getUndeliveredMessages(username);

            if (!undeliveredMessages.isEmpty()) {
                System.out.println("📨 Sending " + undeliveredMessages.size() +
                        " undelivered messages to " + username);

                for (Message message : undeliveredMessages) {
                    MessageDto messageDto = messageMapper.toDto(
                            messageService.getMessageWithUsers(message.getId())
                    );

                    messagingTemplate.convertAndSendToUser(
                            username,
                            "/queue/messages",
                            messageDto
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending undelivered messages to " + username +
                    ": " + e.getMessage());
        }
    }
}