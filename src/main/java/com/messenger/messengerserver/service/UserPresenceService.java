package com.messenger.messengerserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserPresenceService {

    private static final String USER_SESSION_KEY = "user:session:";      // username -> sessionId (одна сессия)
    private static final String ONLINE_USERS_KEY = "online:users";

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration ONLINE_TTL = Duration.ofHours(24);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Подключение пользователя - сохраняем одну сессию
     * Если была старая сессия - перезаписываем
     */
    public void userConnected(String username, String sessionId) {
        String sessionKey = USER_SESSION_KEY + username;

        // Сохраняем новую сессию (перезаписывает старую)
        redisTemplate.opsForValue().set(sessionKey, sessionId, SESSION_TTL);

        // Добавляем в список онлайн
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
        redisTemplate.expire(ONLINE_USERS_KEY, ONLINE_TTL);

        System.out.printf("🟢 [Redis] %s connected. Session: %s%n",
                username, sessionId.substring(0, Math.min(8, sessionId.length())));
    }

    /**
     * Отключение пользователя
     */
    public void userDisconnected(String username, String sessionId) {
        String sessionKey = USER_SESSION_KEY + username;

        // Проверяем, что это та же сессия
        String storedSessionId = redisTemplate.opsForValue().get(sessionKey);

        if (storedSessionId != null && storedSessionId.equals(sessionId)) {
            redisTemplate.delete(sessionKey);
        }

        // Удаляем из онлайн списка
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);

        System.out.printf("🔴 [Redis] %s disconnected. Session: %s%n",
                username, sessionId.substring(0, Math.min(8, sessionId.length())));
    }

    /**
     * Проверка, онлайн ли пользователь
     */
    public boolean isUserOnline(String username) {
        String sessionKey = USER_SESSION_KEY + username;
        String sessionId = redisTemplate.opsForValue().get(sessionKey);

        boolean isOnline = sessionId != null;
        System.out.printf("🔍 [Redis] %s online status: %s%n",
                username, isOnline ? "🟢" : "🔴");

        return isOnline;
    }

    /**
     * Проверка, активна ли конкретная сессия
     */
    public boolean isSessionActive(String username, String sessionId) {
        String sessionKey = USER_SESSION_KEY + username;
        String storedSessionId = redisTemplate.opsForValue().get(sessionKey);

        boolean isActive = storedSessionId != null && storedSessionId.equals(sessionId);
        if (!isActive && storedSessionId != null) {
            System.out.printf("[SESSION] ⚠️ Сессия для %s не совпадает. Ожидалась: %s, получена: %s%n",
                    username, storedSessionId.substring(0, Math.min(8, storedSessionId.length())),
                    sessionId.substring(0, Math.min(8, sessionId.length())));
        }

        return isActive;
    }

    /**
     * Получить текущую сессию пользователя
     */
    public String getUserSession(String username) {
        return redisTemplate.opsForValue().get(USER_SESSION_KEY + username);
    }

    /**
     * Получить список онлайн пользователей
     */
    public Set<String> getOnlineUsers() {
        Set<String> onlineUsers = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
        if (onlineUsers == null) {
            onlineUsers = new HashSet<>();
        }
        System.out.printf("📡 [Redis] Online users: %d%n", onlineUsers.size());
        return onlineUsers;
    }

    public Long getOnlineUsersCount() {
        Long count = redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
        return count != null ? count : 0L;
    }

    public List<String> getAllOnlineUsers() {
        return new ArrayList<>(getOnlineUsers());
    }

    /**
     * Очистить все сессии
     */
    public void clearAllUserSessions(String username) {
        redisTemplate.delete(USER_SESSION_KEY + username);
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);
        System.out.printf("🗑️ [Redis] All sessions cleared for %s%n", username);
    }

    public void clearAllSessions() {
        Set<String> onlineUsers = getOnlineUsers();
        for (String user : onlineUsers) {
            redisTemplate.delete(USER_SESSION_KEY + user);
        }
        redisTemplate.delete(ONLINE_USERS_KEY);
        System.out.printf("🧹 [Redis] Cleared all sessions. Affected users: %d%n", onlineUsers.size());
    }
}