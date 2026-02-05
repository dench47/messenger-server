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

        String sessionId = headerAccessor.getSessionId();

        if (username != null) {
            userService.userConnected(username, sessionId);
            broadcastOnlineUsers();

            // Отправляем персональный список онлайн пользователей новому подключившемуся
            sendPersonalOnlineUsers(username);

            System.out.println("✅ User CONNECTED: " + username +
                    " (session: " + sessionId.substring(0, Math.min(8, sessionId.length())) + ")");
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
            broadcastOnlineUsers();
            System.out.println("🔴 User DISCONNECTED: " + username +
                    " (session: " + sessionId.substring(0, Math.min(8, sessionId.length())) + ")");
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
}