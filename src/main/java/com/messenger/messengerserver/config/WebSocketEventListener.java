package com.messenger.messengerserver.config;

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

    @EventListener
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
            broadcastOnlineUsers();
            System.out.println("✅ User CONNECTED and online: " + username);
        } else {
            System.out.println("⚠️  WebSocket connected but no authenticated user");
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // Получаем пользователя из атрибутов сессии
        String username = null;
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            username = (String) sessionAttributes.get("username");
        }

        // Если не нашли в атрибутах, пробуем из SecurityContext
        if (username == null && headerAccessor.getUser() != null) {
            username = headerAccessor.getUser().getName();
        }

        String sessionId = headerAccessor.getSessionId();

        if (username != null) {
            userService.userDisconnected(username, sessionId);
            broadcastOnlineUsers();
            System.out.println("🔴 User DISCONNECTED and offline: " + username);
        } else {
            System.out.println("⚠️  WebSocket disconnected but no user info");
        }
    }

    private void broadcastOnlineUsers() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            messagingTemplate.convertAndSend("/topic/online.users", onlineUsers);
            System.out.println("📢 Broadcasted online users: " + onlineUsers);
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting online users: " + e.getMessage());
        }
    }
}