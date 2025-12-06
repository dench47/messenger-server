package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Мапа для хранения активных WebSocket сессий: username -> sessionId
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query);
    }

    public void setUserOnline(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnline(true);
        userRepository.save(user);
        System.out.println("✅ User online: " + username);
    }

    public void setUserOffline(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnline(false);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
        System.out.println("🔴 User offline: " + username);
    }

    public void updateLastSeen(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
        System.out.println("⏰ Last seen updated for " + username + ": " + user.getLastSeen());
    }

    // Методы для управления WebSocket сессиями
    public void userConnected(String username, String sessionId) {
        userSessions.put(username, sessionId);
        setUserOnline(username);
        System.out.println("✅ User connected: " + username + " (Sessions: " + userSessions.size() + ")");
    }

    public void userDisconnected(String username, String sessionId) {
        String currentSessionId = userSessions.get(username);

        // Удаляем только если sessionId совпадает (защита от race condition)
        if (sessionId.equals(currentSessionId)) {
            userSessions.remove(username);

            // Если нет активных сессий - ставим офлайн
            if (!userSessions.containsKey(username)) {
                setUserOffline(username);
                updateLastSeen(username); // Явно обновляем last seen
            }
        }

        System.out.println("🔴 User disconnected: " + username + " (Sessions: " + userSessions.size() + ")");
    }

    public void forceDisconnectUser(String username) {
        // Удаляем все сессии пользователя
        userSessions.remove(username);
        setUserOffline(username);
        System.out.println("🔴 Force disconnected user: " + username);
    }

    public boolean isUserOnline(String username) {
        return userSessions.containsKey(username);
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(userSessions.keySet());
    }

    public int getOnlineUsersCount() {
        return userSessions.size();
    }

    // Получаем пользователей с реальным онлайн статусом
    public List<User> getUsersWithRealOnlineStatus() {
        List<User> allUsers = userRepository.findAll();
        List<String> onlineUsernames = getOnlineUsers();

        return allUsers.stream()
                .map(user -> {
                    // Обновляем онлайн статус на основе активных сессий
                    boolean isActuallyOnline = onlineUsernames.contains(user.getUsername());
                    user.setOnline(isActuallyOnline);
                    return user;
                })
                .toList();
    }
}