package com.messenger.messengerserver.config;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @EventListener  // ← ЭТОЙ АННОТАЦИИ НЕ БЫЛО!
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // Получаем аутентифицированного пользователя
        String username = null;
        if (headerAccessor.getUser() != null) {
            username = headerAccessor.getUser().getName();
        }

        // Также проверяем атрибуты сессии
        if (username == null) {
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes != null) {
                username = (String) sessionAttributes.get("username");
            }
        }

        String sessionId = headerAccessor.getSessionId();

        if (username != null) {
            userService.userConnected(username, sessionId);

            // ТОЛЬКО broadcast всем (включая новичка)
            broadcastOnlineUsers();

            System.out.println("✅ User CONNECTED and online: " + username);
        } else {
            System.out.println("⚠️  WebSocket connected but no authenticated user");
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

        String sessionId = headerAccessor.getSessionId();

        if (username != null) {
            userService.userDisconnected(username, sessionId);

            // 1. Отправляем обновленный список онлайн пользователей
            broadcastOnlineUsers();

            // 2. Отправляем отдельное событие с данными об отключившемся пользователе
            sendUserDisconnectedEvent(username);

            System.out.println("🔴 User DISCONNECTED and offline: " + username);
        } else {
            System.out.println("⚠️  WebSocket disconnected but no user info");
        }
    }

    private void sendUserDisconnectedEvent(String username) {
        try {
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> disconnectEvent = new HashMap<>();
            disconnectEvent.put("type", "USER_DISCONNECTED");
            disconnectEvent.put("username", username);
            disconnectEvent.put("online", false);
            disconnectEvent.put("lastSeen", user.getLastSeen());
            disconnectEvent.put("lastSeenText", formatLastSeenForEvent(user.getLastSeen()));

            messagingTemplate.convertAndSend("/topic/user.events", disconnectEvent);
            System.out.println("📢 Sent disconnect event for user: " + username);
        } catch (Exception e) {
            System.err.println("❌ Error sending disconnect event: " + e.getMessage());
        }
    }

    private String formatLastSeenForEvent(LocalDateTime lastSeen) {
        if (lastSeen == null) return "never";

        Duration duration = Duration.between(lastSeen, LocalDateTime.now());
        long minutes = duration.toMinutes();

        if (minutes < 1) return "just now";
        if (minutes == 1) return "1 minute ago";
        if (minutes < 60) return minutes + " minutes ago";

        long hours = duration.toHours();
        if (hours == 1) return "1 hour ago";
        if (hours < 24) return hours + " hours ago";

        long days = duration.toDays();
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";

        return "long time ago";
    }

    private void broadcastOnlineUsers() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            messagingTemplate.convertAndSend("/topic/online.users", onlineUsers);
            System.out.println("📢 Broadcasted online users to ALL: " + onlineUsers);
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting online users: " + e.getMessage());
        }
    }
}