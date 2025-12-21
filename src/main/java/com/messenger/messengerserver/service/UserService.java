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

        // Активен если была активность менее ONLINE_THRESHOLD_MINUTES минут назад
        LocalDateTime activeThreshold = LocalDateTime.now().minusMinutes(ONLINE_THRESHOLD_MINUTES);
        return user.getLastActivity().isAfter(activeThreshold);
    }


    @Scheduled(fixedRate = BROADCAST_INTERVAL_MS)
    public void broadcastUserStatusUpdates() {
        try {
            List<User> allUsers = userRepository.findAll();
            if (allUsers.isEmpty()) return;

            System.out.println("🔄 Scheduled status broadcast for " + allUsers.size() + " users");

            for (User user : allUsers) {
                String username = user.getUsername();

                boolean hasWebSocket = userSessions.containsKey(username);
                boolean isActuallyActive = isUserActuallyActive(username); // lastActivity < 2 мин

                String status;
                String displayText = null;
                boolean showAsOnline = false;

                // НОВАЯ ЛОГИКА WHATSAPP:
                if (hasWebSocket) {
                    if (isActuallyActive) {
                        // Активно в приложении (< 2 мин)
                        status = "active";
                        displayText = "online";
                        showAsOnline = true;
                        System.out.println("   👤 " + username + ": ОНЛАЙН (активен < 2 мин)");
                    } else {
                        // В фоне (> 2 мин), но WebSocket есть
                        LocalDateTime lastActivity = user.getLastActivity();
                        if (lastActivity != null) {
                            Duration inactiveDuration = Duration.between(lastActivity, LocalDateTime.now());
                            long inactiveMinutes = inactiveDuration.toMinutes();

                            if (inactiveMinutes < 5) {
                                status = "inactive";
                                displayText = formatTimeAgo(lastActivity); // "2 мин назад"
                                showAsOnline = false; // ← НЕ показываем как онлайн!
                                System.out.println("   👤 " + username + ": В ФОНЕ (2-5 мин): " + displayText);
                            } else {
                                // В фоне > 5 минут: "Был в 14:30"
                                status = "offline";
                                displayText = formatLastSeenForDisplay(lastActivity); // "Был в 14:30"
                                showAsOnline = false;
                                System.out.println("   👤 " + username + ": В ФОНЕ (>5 мин): " + displayText);
                            }
                        } else {
                            status = "inactive";
                            displayText = "был недавно";
                            showAsOnline = false;
                            System.out.println("   👤 " + username + ": В ФОНЕ (нет lastActivity)");
                        }
                    }
                } else {
                    // Нет WebSocket (приложение закрыто)
                    status = "offline";
                    LocalDateTime lastSeen = user.getLastSeen();

                    if (lastSeen != null) {
                        displayText = formatLastSeenForDisplay(lastSeen); // "Был в 14:30"
                        System.out.println("   👤 " + username + ": ОФФЛАЙН: " + displayText);
                    } else {
                        displayText = "никогда";
                        System.out.println("   👤 " + username + ": ОФФЛАЙН (никогда не был)");
                    }
                    showAsOnline = false;
                }

                Map<String, Object> statusUpdate = new HashMap<>();
                statusUpdate.put("type", "USER_STATUS_UPDATE");
                statusUpdate.put("username", username);
                statusUpdate.put("online", showAsOnline); // true ТОЛЬКО если активно в приложении (<2 мин)
                statusUpdate.put("active", isActuallyActive);
                statusUpdate.put("status", status);
                statusUpdate.put("lastSeenText", displayText);

                messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);
            }

            System.out.println("✅ Status broadcast completed");
        } catch (Exception e) {
            System.err.println("❌❌❌ ERROR in status broadcast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String formatLastSeenForDisplay(LocalDateTime lastSeen) {
        if (lastSeen == null) return "никогда";

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM");
        DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yy");

        // 1. Если сегодня
        if (lastSeen.toLocalDate().equals(now.toLocalDate())) {
            return "Был в " + lastSeen.format(timeFormatter);
        }
        // 2. Если вчера
        else if (lastSeen.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "Был вчера в " + lastSeen.format(timeFormatter);
        }
        // 3. Если на этой неделе (послезавтра - 6 дней назад)
        else if (lastSeen.isAfter(now.minusDays(7))) {
            // Просто показываем дату без названия дня
            return "Был " + lastSeen.format(dateFormatter) + " в " + lastSeen.format(timeFormatter);
        }
        // 4. Если в этом году
        else if (lastSeen.getYear() == now.getYear()) {
            return "Был " + lastSeen.format(dateFormatter) + " в " + lastSeen.format(timeFormatter);
        }
        // 5. Если давно (больше года)
        else {
            return "Был " + lastSeen.format(fullDateFormatter) + " в " + lastSeen.format(timeFormatter);
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

    // Добавьте этот метод в UserService
    public User saveUser(User user) {
        return userRepository.save(user);
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