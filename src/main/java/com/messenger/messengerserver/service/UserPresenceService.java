package com.messenger.messengerserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class UserPresenceService {

    // Ключи Redis
    private static final String USER_SESSIONS_PREFIX = "user:sessions:";  // Множество сессий пользователя
    private static final String ONLINE_USERS_KEY = "online:users";         // Множество онлайн пользователей
    private static final String USER_DEVICE_COUNT = "user:devices:";       // Количество устройств

    // TTL настройки
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);    // Сессия живет 30 минут
    private static final Duration ONLINE_TTL = Duration.ofHours(24);       // Онлайн статус 24 часа
    private static final Duration DEVICE_TTL = Duration.ofMinutes(35);     // Немного больше сессии

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Пользователь подключился с нового устройства/сессии
     */
    public void userConnected(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();

        // 1. Добавляем сессию в множество сессий пользователя
        setOps.add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, SESSION_TTL);

        // 2. Добавляем пользователя в онлайн
        setOps.add(ONLINE_USERS_KEY, username);
        redisTemplate.expire(ONLINE_USERS_KEY, ONLINE_TTL);

        // 3. Обновляем счетчик устройств
        Long deviceCount = setOps.size(userSessionsKey);
        redisTemplate.opsForValue().set(userDevicesKey,
                String.valueOf(deviceCount != null ? deviceCount : 1),
                DEVICE_TTL);

        System.out.printf("🟢 [Redis] %s connected. Session: %s, Devices: %d%n",
                username, sessionId.substring(0, Math.min(8, sessionId.length())),
                deviceCount != null ? deviceCount : 1);
    }

    /**
     * Пользователь отключился (конкретная сессия)
     */
    public void userDisconnected(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();

        // 1. Удаляем конкретную сессию
        Long removed = setOps.remove(userSessionsKey, sessionId);

        if (removed != null && removed > 0) {
            // 2. Получаем оставшиеся сессии
            Long remainingSessions = setOps.size(userSessionsKey);

            if (remainingSessions == null || remainingSessions == 0) {
                // 3. Если сессий не осталось - удаляем из онлайн
                setOps.remove(ONLINE_USERS_KEY, username);
                redisTemplate.delete(userSessionsKey);
                redisTemplate.delete(userDevicesKey);

                System.out.printf("🔴 [Redis] %s fully disconnected. Session: %s%n",
                        username, sessionId.substring(0, Math.min(8, sessionId.length())));
            } else {
                // 4. Обновляем счетчик устройств
                redisTemplate.opsForValue().set(userDevicesKey,
                        String.valueOf(remainingSessions), DEVICE_TTL);

                System.out.printf("🔴 [Redis] %s session removed. Remaining devices: %d, Session: %s%n",
                        username, remainingSessions,
                        sessionId.substring(0, Math.min(8, sessionId.length())));
            }
        } else {
            System.out.printf("⚠️ [Redis] Session not found for %s: %s%n",
                    username, sessionId.substring(0, Math.min(8, sessionId.length())));
        }
    }

    /**
     * Проверить онлайн статус пользователя
     */
    public boolean isUserOnline(String username) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Long sessionCount = setOps.size(userSessionsKey);

        boolean isOnline = sessionCount != null && sessionCount > 0;
        System.out.printf("🔍 [Redis] %s online status: %s (devices: %d)%n",
                username, isOnline ? "🟢" : "🔴", sessionCount != null ? sessionCount : 0);

        return isOnline;
    }

    /**
     * Получить всех онлайн пользователей
     */
    public Set<String> getOnlineUsers() {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Set<String> onlineUsers = setOps.members(ONLINE_USERS_KEY);

        if (onlineUsers == null) {
            onlineUsers = new HashSet<>();
        }

        System.out.printf("📡 [Redis] Online users: %d%n", onlineUsers.size());
        return onlineUsers;
    }

    /**
     * Получить количество онлайн пользователей
     */
    public Long getOnlineUsersCount() {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Long count = setOps.size(ONLINE_USERS_KEY);
        return count != null ? count : 0L;
    }

    /**
     * Получить количество устройств пользователя
     */
    public int getUserDeviceCount(String username) {
        String userDevicesKey = USER_DEVICE_COUNT + username;
        String countStr = redisTemplate.opsForValue().get(userDevicesKey);

        if (countStr != null) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Если нет в кеше, считаем из множества сессий
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Long count = setOps.size(userSessionsKey);

        int deviceCount = count != null ? count.intValue() : 0;
        if (deviceCount > 0) {
            redisTemplate.opsForValue().set(userDevicesKey,
                    String.valueOf(deviceCount), DEVICE_TTL);
        }

        return deviceCount;
    }

    /**
     * Обновить TTL сессии (при активности)
     */
    public void refreshSession(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        // Проверяем, существует ли сессия
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Boolean isMember = setOps.isMember(userSessionsKey, sessionId);

        if (Boolean.TRUE.equals(isMember)) {
            // Обновляем TTL для множества сессий
            redisTemplate.expire(userSessionsKey, SESSION_TTL);

            // Обновляем TTL для счетчика устройств
            Long deviceCount = setOps.size(userSessionsKey);
            if (deviceCount != null && deviceCount > 0) {
                redisTemplate.opsForValue().set(userDevicesKey,
                        String.valueOf(deviceCount), DEVICE_TTL);
            }

            System.out.printf("🔄 [Redis] Session refreshed for %s, devices: %d%n",
                    username, deviceCount != null ? deviceCount : 0);
        }
    }

    /**
     * Очистить все сессии пользователя (при logout)
     */
    public void clearAllUserSessions(String username) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        // Удаляем из онлайн пользователей
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        setOps.remove(ONLINE_USERS_KEY, username);

        // Удаляем все ключи пользователя
        redisTemplate.delete(userSessionsKey);
        redisTemplate.delete(userDevicesKey);

        System.out.printf("🗑️ [Redis] All sessions cleared for %s%n", username);
    }

    /**
     * Очистить все сессии (административная функция)
     */
    public void clearAllSessions() {
        // Получаем всех онлайн пользователей
        Set<String> onlineUsers = getOnlineUsers();

        // Удаляем онлайн пользователей
        redisTemplate.delete(ONLINE_USERS_KEY);

        // Удаляем все сессии и счетчики
        for (String user : onlineUsers) {
            redisTemplate.delete(USER_SESSIONS_PREFIX + user);
            redisTemplate.delete(USER_DEVICE_COUNT + user);
        }

        System.out.printf("🧹 [Redis] Cleared all sessions. Affected users: %d%n", onlineUsers.size());
    }
}