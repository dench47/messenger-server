package com.messenger.messengerserver.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class UserPresenceService {

    private static final String USER_SESSIONS_PREFIX = "user:sessions:";
    private static final String ONLINE_USERS_KEY = "online:users";
    private static final String USER_DEVICE_COUNT = "user:devices:";

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration ONLINE_TTL = Duration.ofHours(24);
    private static final Duration DEVICE_TTL = Duration.ofMinutes(35);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void userConnected(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();

        setOps.add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, SESSION_TTL);

        setOps.add(ONLINE_USERS_KEY, username);
        redisTemplate.expire(ONLINE_USERS_KEY, ONLINE_TTL);

        Long deviceCount = setOps.size(userSessionsKey);
        redisTemplate.opsForValue().set(userDevicesKey,
                String.valueOf(deviceCount != null ? deviceCount : 1),
                DEVICE_TTL);

        System.out.printf("🟢 [Redis] %s connected. Session: %s, Devices: %d%n",
                username, sessionId.substring(0, Math.min(8, sessionId.length())),
                deviceCount != null ? deviceCount : 1);
    }

    public void userDisconnected(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();

        Long removed = setOps.remove(userSessionsKey, sessionId);

        if (removed != null && removed > 0) {
            Long remainingSessions = setOps.size(userSessionsKey);

            if (remainingSessions == null || remainingSessions == 0) {
                setOps.remove(ONLINE_USERS_KEY, username);
                redisTemplate.delete(userSessionsKey);
                redisTemplate.delete(userDevicesKey);

                System.out.printf("🔴 [Redis] %s fully disconnected. Session: %s%n",
                        username, sessionId.substring(0, Math.min(8, sessionId.length())));
            } else {
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

    public boolean isUserOnline(String username) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Long sessionCount = setOps.size(userSessionsKey);

        boolean isOnline = sessionCount != null && sessionCount > 0;
        System.out.printf("🔍 [Redis] %s online status: %s (devices: %d)%n",
                username, isOnline ? "🟢" : "🔴", sessionCount != null ? sessionCount : 0);

        return isOnline;
    }

    public Set<String> getOnlineUsers() {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Set<String> onlineUsers = setOps.members(ONLINE_USERS_KEY);

        if (onlineUsers == null) {
            onlineUsers = new HashSet<>();
        }

        System.out.printf("📡 [Redis] Online users: %d%n", onlineUsers.size());
        return onlineUsers;
    }

    public Long getOnlineUsersCount() {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Long count = setOps.size(ONLINE_USERS_KEY);
        return count != null ? count : 0L;
    }

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

    public void refreshSession(String username, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Boolean isMember = setOps.isMember(userSessionsKey, sessionId);

        if (Boolean.TRUE.equals(isMember)) {
            redisTemplate.expire(userSessionsKey, SESSION_TTL);

            Long deviceCount = setOps.size(userSessionsKey);
            if (deviceCount != null && deviceCount > 0) {
                redisTemplate.opsForValue().set(userDevicesKey,
                        String.valueOf(deviceCount), DEVICE_TTL);
            }

            System.out.printf("🔄 [Redis] Session refreshed for %s, devices: %d%n",
                    username, deviceCount != null ? deviceCount : 0);
        }
    }

    public void clearAllUserSessions(String username) {
        String userSessionsKey = USER_SESSIONS_PREFIX + username;
        String userDevicesKey = USER_DEVICE_COUNT + username;

        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        setOps.remove(ONLINE_USERS_KEY, username);

        redisTemplate.delete(userSessionsKey);
        redisTemplate.delete(userDevicesKey);

        System.out.printf("🗑️ [Redis] All sessions cleared for %s%n", username);
    }

    public void clearAllSessions() {
        Set<String> onlineUsers = getOnlineUsers();

        redisTemplate.delete(ONLINE_USERS_KEY);

        for (String user : onlineUsers) {
            redisTemplate.delete(USER_SESSIONS_PREFIX + user);
            redisTemplate.delete(USER_DEVICE_COUNT + user);
        }

        System.out.printf("🧹 [Redis] Cleared all sessions. Affected users: %d%n", onlineUsers.size());
    }

    public List<String> getAllOnlineUsers() {
        return new ArrayList<>(getOnlineUsers());
    }
}