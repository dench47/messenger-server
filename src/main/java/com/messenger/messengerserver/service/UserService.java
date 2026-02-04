package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private static final int BROADCAST_INTERVAL_MS = 35000;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query);
    }

    public void updateLastSeen(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);

        // МГНОВЕННЫЙ статус при уходе в фон
        sendImmediateStatusUpdate(username, false);
        System.out.println("⏰ Last seen updated for " + username + ": " + user.getLastSeen());
    }

    public void userConnected(String username, String sessionId) {
        userSessions.put(username, sessionId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnline(true);
        user.setLastSeen(null);
        userRepository.save(user);

        System.out.println("👤 " + username + ": 🟢 CONNECTED");

        // МГНОВЕННЫЙ статус при подключении
        sendImmediateStatusUpdate(username, true);
        System.out.println("✅ User connected: " + username);
    }

    public void userDisconnected(String username, String sessionId) {
        String currentSessionId = userSessions.get(username);

        if (sessionId.equals(currentSessionId)) {
            userSessions.remove(username);

            if (!userSessions.containsKey(username)) {
                User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);

                System.out.println("👤 " + username + ": 🔴 DISCONNECTED (last seen: " +
                        formatLastSeenDetailed(user.getLastSeen()) + ")");

                // МГНОВЕННЫЙ статус при отключении
                sendImmediateStatusUpdate(username, false);
            }
        }

        System.out.println("🔴 User disconnected: " + username + " (Active sessions: " + userSessions.size() + ")");
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

    private Map<String, Object> prepareStatusData(User user) {
        String username = user.getUsername();
        boolean hasWebSocket = userSessions.containsKey(username);

        String status = hasWebSocket ? "online" : "offline";
        String lastSeenText = hasWebSocket ? "online" : formatLastSeenDetailed(user.getLastSeen());

        String emoji = hasWebSocket ? "🟢" : "🔴";
        System.out.println("👤 " + username + ": " + emoji + " " + status + " (" + lastSeenText + ")");

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("type", "USER_STATUS_UPDATE");
        statusData.put("username", username);
        statusData.put("online", hasWebSocket);
        statusData.put("status", status);
        statusData.put("lastSeenText", lastSeenText);

        return statusData;
    }

    // МГНОВЕННОЕ обновление статуса одного пользователя
    private void sendImmediateStatusUpdate(String username, boolean isOnline) {
        try {
            User user = findByUsername(username).orElse(null);
            if (user == null) return;

            Map<String, Object> statusUpdate = prepareStatusData(user);
            messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);

            System.out.println("⚡ IMMEDIATE STATUS: " + username + " -> " +
                    (isOnline ? "🟢 online" : "🔴 " + statusUpdate.get("lastSeenText")));
        } catch (Exception e) {
            System.err.println("❌ Error sending immediate status: " + e.getMessage());
        }
    }

    // Периодическая рассылка статусов всех пользователей
//    @Scheduled(fixedRate = BROADCAST_INTERVAL_MS)
//    public void broadcastAllUserStatuses() {
//        try {
//            List<User> allUsers = userRepository.findAll();
//            if (allUsers.isEmpty()) return;
//
//            System.out.println("🔄 Broadcasting statuses for " + allUsers.size() + " users");
//
//            for (User user : allUsers) {
//                Map<String, Object> statusUpdate = prepareStatusData(user);
//                messagingTemplate.convertAndSend("/topic/user.events", statusUpdate);
//            }
//
//            System.out.println("✅ Status broadcast completed");
//        } catch (Exception e) {
//            System.err.println("❌ Error in status broadcast: " + e.getMessage());
//        }
//    }

    public void save(User user) {
        userRepository.save(user);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public void updateUserInDatabase(String username, boolean online) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setOnline(online);
        if (!online) {
            user.setLastSeen(LocalDateTime.now());
        } else {
            user.setLastSeen(null);
        }

        userRepository.save(user);
        System.out.println("💾 Database updated: " + username + " = " + (online ? "online" : "offline"));
    }
}