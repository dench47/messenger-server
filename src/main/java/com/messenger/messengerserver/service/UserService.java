package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired  // ← ДОБАВЬ ЭТО
    private SimpMessagingTemplate messagingTemplate;


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
                    // Определяем онлайн по WebSocket
                    boolean isActuallyOnline = onlineUsernames.contains(user.getUsername());
                    user.setOnline(isActuallyOnline);

                    // Если онлайн, проверяем активность
                    if (isActuallyOnline && user.getLastActivity() != null) {
                        LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
                        boolean isActive = user.getLastActivity().isAfter(twoMinutesAgo);
                        // Можно добавить поле "active" или использовать существующее
                        // user.setActive(isActive); // если добавишь поле
                    }

                    return user;
                })
                .toList();
    }

    public void updateUserOnlineStatus(String username, boolean online) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Если ставим онлайн, а WebSocket уже есть - игнорируем
        if (online && userSessions.containsKey(username)) {
            System.out.println("⚠️ User already online via WebSocket: " + username);
            return;
        }

        // Если ставим оффлайн, но есть WebSocket сессия - WebSocket главный
        if (!online && userSessions.containsKey(username)) {
            System.out.println("⚠️ User has active WebSocket, keeping online: " + username);
            return;
        }

        user.setOnline(online);
        if (!online) {
            user.setLastSeen(LocalDateTime.now());
        }
        userRepository.save(user);

        System.out.println((online ? "✅" : "🔴") + " User status via API: " + username + " = " + online);
    }

    public void updateUserActivity(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastActivity(LocalDateTime.now());
        userRepository.save(user);
        System.out.println("🔄 Activity updated for: " + username);
    }

    public boolean isUserActive(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getLastActivity() == null) {
            return user.getOnline(); // Если нет активности, смотрим онлайн статус
        }

        // Считаем активным, если была активность в последние 2 минуты
        LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
        return user.getLastActivity().isAfter(twoMinutesAgo) && user.getOnline();
    }

    public boolean isUserActuallyActive(String username) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        if (user.getLastActivity() == null) {
            return false;
        }

        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        return user.getLastActivity().isAfter(oneMinuteAgo);
    }

    @Scheduled(fixedRate = 30000)
    public void broadcastUserStatusUpdates() {
        try {
            List<User> allUsers = userRepository.findAll();
            if (allUsers.isEmpty()) return;

            System.out.println("🔄 Scheduled status broadcast for " + allUsers.size() + " users");

            for (User user : allUsers) {
                String username = user.getUsername();

                boolean hasWebSocket = userSessions.containsKey(username);
                boolean isActuallyActive = isUserActuallyActive(username);
                boolean isOnline = hasWebSocket;

                String status;
                String displayText = null; // Текст для отображения

                if (isOnline) {
                    // Онлайн пользователь
                    if (isActuallyActive) {
                        status = "active";
                        displayText = "online";
                    } else {
                        status = "inactive";
                        // ДЛЯ INACTIVE пользователей показываем "был X назад"!
                        if (user.getLastActivity() != null) {
                            displayText = formatTimeAgo(user.getLastActivity());
                        } else {
                            displayText = "был недавно";
                        }
                    }
                    System.out.println("   👤 " + username + ": онлайн, active=" + isActuallyActive +
                            ", display=" + displayText);
                } else {
                    // Оффлайн пользователь
                    status = "offline";
                    if (user.getLastSeen() != null) {
                        displayText = formatTimeAgo(user.getLastSeen());
                    } else {
                        displayText = "никогда";
                    }
                    System.out.println("   👤 " + username + ": оффлайн, lastSeen=" +
                            user.getLastSeen() + " -> " + displayText);
                }

                Map<String, Object> statusUpdate = new HashMap<>();
                statusUpdate.put("type", "USER_STATUS_UPDATE");
                statusUpdate.put("username", username);
                statusUpdate.put("online", isOnline);
                statusUpdate.put("active", isActuallyActive);
                statusUpdate.put("status", status);

                // ВСЕГДА отправляем displayText!
                if (displayText != null) {
                    statusUpdate.put("lastSeenText", displayText);
                }

                messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);
            }

            System.out.println("✅ Status broadcast completed");
        } catch (Exception e) {
            System.err.println("❌❌❌ ERROR in status broadcast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String formatTimeAgo(LocalDateTime time) {
        if (time == null) return "никогда";

        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = duration.toMinutes();

        if (minutes < 1) return "только что";
        if (minutes == 1) return "1 минуту назад";
        if (minutes < 5) return minutes + " минуты назад";
        if (minutes < 60) return minutes + " минут назад";

        long hours = duration.toHours();
        if (hours == 1) return "1 час назад";
        if (hours < 5) return hours + " часа назад";
        if (hours < 24) return hours + " часов назад";

        long days = duration.toDays();
        if (days == 1) return "вчера";
        if (days == 2) return "позавчера";
        if (days < 7) return days + " дня назад";
        if (days < 30) return days + " дней назад";

        long months = days / 30;
        if (months == 1) return "месяц назад";
        if (months < 12) return months + " месяцев назад";

        long years = months / 12;
        if (years == 1) return "год назад";
        return years + " лет назад";
    }

    public void save(User user) {
        userRepository.save(user);
    }

    public String determineUserStatus(String username) {
        boolean hasWebSocket = isUserOnline(username);
        boolean isActuallyActive = isUserActuallyActive(username);

        if (!hasWebSocket) {
            return "offline";
        }
        return isActuallyActive ? "active" : "inactive";
    }

}