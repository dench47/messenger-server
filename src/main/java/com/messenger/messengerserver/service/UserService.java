package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.threeten.bp.format.TextStyle;


import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    private static final int ONLINE_THRESHOLD_MINUTES = 2;    // "online" если активен < 2 мин назад
    private static final int RECENTLY_THRESHOLD_MINUTES = 5;  // "был недавно" если < 5 мин
    private static final int BROADCAST_INTERVAL_MS = 30000;   // Рассылка каждые 30 сек

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
        sendImmediateStatusUpdate(username);
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

        public boolean isUserActuallyActive(String username) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getLastActivity() == null) return false;

        // ИЗМЕНЕНИЕ: 1 минута вместо 2
        LocalDateTime activeThreshold = LocalDateTime.now().minusMinutes(1);
        return user.getLastActivity().isAfter(activeThreshold);
    }


    @Scheduled(fixedRate = BROADCAST_INTERVAL_MS)
    public void broadcastUserStatusUpdates() {
        try {
            List<User> allUsers = userRepository.findAll();
            if (allUsers.isEmpty()) return;

            System.out.println("🔄 Scheduled status broadcast for " + allUsers.size() + " users");

            for (User user : allUsers) {
                Map<String, Object> statusUpdate = prepareStatusUpdate(user);

                String username = user.getUsername();
                boolean showAsOnline = (boolean) statusUpdate.get("online");
                String displayText = (String) statusUpdate.get("lastSeenText");

                messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);

                System.out.println("   👤 " + username + ": " +
                        (showAsOnline ? "🟢" : "🔴") + " " + displayText);
            }

            System.out.println("✅ Status broadcast completed");
        } catch (Exception e) {
            System.err.println("❌❌❌ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }




    public void save(User user) {
        userRepository.save(user);
    }

    // Добавьте этот метод в UserService
    public User saveUser(User user) {
        return userRepository.save(user);
    }



    public class StatusFormatter {

        public static String formatStatusForDisplay(User user, boolean hasWebSocket) {
            if (user == null) return "offline";

            LocalDateTime lastSeen = user.getLastSeen();
            LocalDateTime lastActivity = user.getLastActivity();
            LocalDateTime referenceTime = lastActivity != null ? lastActivity : lastSeen;

            if (hasWebSocket) {
                if (referenceTime != null) {
                    Duration duration = Duration.between(referenceTime, LocalDateTime.now());
                    long minutes = duration.toMinutes();

                    // ИЗМЕНЕНИЕ: 1 минута вместо 2
                    if (minutes < 1) {
                        return "online";
                    } else if (minutes < 5) {
                        return minutes + " мин назад";
                    } else if (minutes < 60) {
                        return minutes + " минут назад";
                    }
                }
                return "был недавно";
            } else {
                // Нет WebSocket - точно оффлайн
                return formatLastSeenDetailed(referenceTime);
            }
        }

        public static String formatLastSeenDetailed(LocalDateTime time) {
            if (time == null) return "никогда";

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM");

            if (time.toLocalDate().equals(now.toLocalDate())) {
                return "Был в " + time.format(timeFormatter);
            } else if (time.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                return "Был вчера в " + time.format(timeFormatter);
            } else if (time.isAfter(now.minusDays(7))) {
                return "Был " + time.format(dateFormatter) + " в " + time.format(timeFormatter);
            } else if (time.getYear() == now.getYear()) {
                return "Был " + time.format(dateFormatter) + " в " + time.format(timeFormatter);
            } else {
                DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy");
                return "Был " + time.format(fullDateFormatter) + " в " + time.format(timeFormatter);
            }
        }
    }

    public void sendImmediateStatusUpdate(String username) {
        try {
            User user = findByUsername(username).orElse(null);
            if (user == null) return;

            Map<String, Object> statusUpdate = prepareStatusUpdate(user);
            messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);

            System.out.println("⚡ IMMEDIATE STATUS: " + username + " -> " +
                    statusUpdate.get("lastSeenText"));
        } catch (Exception e) {
            System.err.println("❌ Error sending immediate status: " + e.getMessage());
        }
    }

    // В методе prepareStatusUpdate(User user) ИЗМЕНЯЕМ логику:
    private Map<String, Object> prepareStatusUpdate(User user) {
        String username = user.getUsername();
        boolean hasWebSocket = userSessions.containsKey(username);
        boolean isActuallyActive = isUserActuallyActive(username);

        // Определяем статус и текст
        String status;
        String displayText;
        boolean showAsOnline;

        if (hasWebSocket) {
            if (isActuallyActive) {
                // Активно в приложении (< 1 мин)
                status = "active";
                displayText = "online";
                showAsOnline = true;
            } else {
                // В фоне (> 1 мин)
                LocalDateTime lastActivity = user.getLastActivity();
                LocalDateTime lastSeen = user.getLastSeen();
                LocalDateTime referenceTime = lastActivity != null ? lastActivity : lastSeen;

                if (referenceTime != null) {
                    Duration duration = Duration.between(referenceTime, LocalDateTime.now());
                    long minutes = duration.toMinutes();

                    if (minutes < 5) {
                        // 1-5 минут: "X минут назад"
                        status = "inactive";
                        displayText = minutes + " мин назад";
                        showAsOnline = false;
                    } else {
                        // >5 минут: "Был в HH:mm" (как при свайпе)
                        status = "offline";
                        displayText = StatusFormatter.formatLastSeenDetailed(referenceTime);
                        showAsOnline = false;
                    }
                } else {
                    status = "inactive";
                    displayText = "был недавно";
                    showAsOnline = false;
                }
            }
        } else {
            // Нет WebSocket
            status = "offline";
            displayText = StatusFormatter.formatLastSeenDetailed(user.getLastSeen());
            showAsOnline = false;
        }

        // Создаем Map
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("type", "USER_STATUS_UPDATE");
        statusUpdate.put("username", username);
        statusUpdate.put("online", showAsOnline);
        statusUpdate.put("active", isActuallyActive);
        statusUpdate.put("status", status);
        statusUpdate.put("lastSeenText", displayText);

        return statusUpdate;
    }

}