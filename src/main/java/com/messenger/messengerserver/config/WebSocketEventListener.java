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

import java.util.HashMap;
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
            // 1. Регистрируем подключение (само ставит онлайн)
            userService.userConnected(username, sessionId);

            // 2. НЕ отправляем immediate status - он отправится через broadcastOnlineUsers()

            // 3. Рассылаем обновленный список онлайн пользователей
            broadcastOnlineUsers();

            System.out.println("✅ User CONNECTED: " + username);
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
            // 1. Регистрируем отключение (само поставит офлайн если нет других сессий)
            userService.userDisconnected(username, sessionId);

            // 2. Рассылаем обновленный список онлайн пользователей
            broadcastOnlineUsers();

            System.out.println("🔴 User DISCONNECTED: " + username);
        } else {
            System.out.println("⚠️  WebSocket disconnected but no user info");
        }
    }

    private void broadcastOnlineUsers() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();

            // Рассылаем всем обновленный список
            messagingTemplate.convertAndSend("/topic/online.users", onlineUsers);

            // Также отправляем отдельные статус-ивенты для каждого пользователя
            for (String username : onlineUsers) {
                sendUserStatusUpdate(username, true);
            }

            System.out.println("📢 Broadcasted online users: " + onlineUsers);
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting online users: " + e.getMessage());
        }
    }

    private void sendUserStatusUpdate(String username, boolean isConnected) {
        try {
            User user = userService.findByUsername(username).orElse(null);
            if (user == null) return;

            // Используем централизованное форматирование из UserService
            boolean hasWebSocket = userService.isUserOnline(username);
            boolean isActuallyActive = userService.isUserActuallyActive(username);

            // Форматируем текст через UserService.StatusFormatter
            String displayText = UserService.StatusFormatter.formatStatusForDisplay(user, hasWebSocket);
            boolean showAsOnline = hasWebSocket;

            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("type", "USER_STATUS_UPDATE");
            statusUpdate.put("username", username);
            statusUpdate.put("online", showAsOnline);
            statusUpdate.put("active", isActuallyActive);
            statusUpdate.put("status", isActuallyActive ? "active" : "inactive");
            statusUpdate.put("lastSeenText", displayText);

            messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);

            System.out.println("⚡ Status update sent: " + username +
                    " -> online=" + showAsOnline + ", text=" + displayText);
        } catch (Exception e) {
            System.err.println("❌ Error sending user status: " + e.getMessage());
        }
    }
}