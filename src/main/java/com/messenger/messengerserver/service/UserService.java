package com.messenger.messengerserver.service;

import com.messenger.messengerserver.model.Contact;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.ContactRepository;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserPresenceService userPresenceService;

    @Autowired
    private ContactRepository contactRepository;

    // Map для хранения sessionId по username (только для WebSocket соединений)
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

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
        // Сохраняем сессию в Redis
        userPresenceService.userConnected(username, sessionId);

        // Сохраняем sessionId локально для быстрого доступа
        userSessionMap.put(username, sessionId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnline(true);
        user.setLastSeen(null);
        userRepository.save(user);

        System.out.println("👤 " + username + ": 🟢 CONNECTED (session: " +
                sessionId.substring(0, Math.min(8, sessionId.length())) + ")");

        // МГНОВЕННЫЙ статус при подключении
        sendImmediateStatusUpdate(username, true);
        System.out.println("✅ User connected: " + username);
    }

    public void userDisconnected(String username, String sessionId) {
        try {
            // Небольшая задержка для клиента, чтобы он успел отправить UNSUBSCRIBE
            Thread.sleep(50); // 50ms задержка

            // Удаляем сессию из Redis
            userPresenceService.userDisconnected(username, sessionId);

            // Удаляем из локальной мапы
            userSessionMap.remove(username);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            System.out.println("👤 " + username + ": 🔴 DISCONNECTED (session: " +
                    sessionId.substring(0, Math.min(8, sessionId.length())) +
                    ", last seen: " + formatLastSeenDetailed(user.getLastSeen()) + ")");

            // МГНОВЕННЫЙ статус при отключении (с задержкой)
            new Thread(() -> {
                try {
                    Thread.sleep(100); // Дополнительная задержка 100ms
                    sendImmediateStatusUpdate(username, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            System.out.println("🔴 User disconnected: " + username);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Error in userDisconnected: " + e.getMessage());
        }
    }

    public boolean isUserOnline(String username) {
        // Проверяем в Redis
        return userPresenceService.isUserOnline(username);
    }

    public List<String> getOnlineUsers() {
        // Получаем из Redis
        return new ArrayList<>(userPresenceService.getOnlineUsers());
    }

    public int getOnlineUsersCount() {
        // Получаем из Redis
        return userPresenceService.getOnlineUsersCount().intValue();
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
        // Проверяем онлайн статус в Redis
        boolean hasWebSocket = userPresenceService.isUserOnline(username);

        String status = hasWebSocket ? "online" : "offline";
        String lastSeenText = hasWebSocket ? "online" : formatLastSeenDetailed(user.getLastSeen());

        String emoji = hasWebSocket ? "🟢" : "🔴";
        System.out.println("👤 " + username + ": " + emoji + " " + status + " (" + lastSeenText + ")");

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("type", "USER_STATUS_UPDATE");
        statusData.put("username", username);
        statusData.put("online", hasWebSocket);  // Это должно быть false при отключении
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
                    (statusUpdate.get("online").equals(true) ? "🟢 online" : "🔴 " + statusUpdate.get("lastSeenText")));
        } catch (Exception e) {
            System.err.println("❌ Error sending immediate status: " + e.getMessage());
        }
    }

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
    }

    /**
     * Обновить TTL сессии пользователя (при активности)
     * Теперь требует sessionId
     */
    public void refreshUserSession(String username, String sessionId) {
        userPresenceService.refreshSession(username, sessionId);
    }

    /**
     * Получить sessionId пользователя
     */
    public String getUserSessionId(String username) {
        return userSessionMap.get(username);
    }

    /**
     * Проверить, есть ли сессия пользователя
     */
    public boolean hasUserSession(String username, String sessionId) {
        String storedSessionId = userSessionMap.get(username);
        return storedSessionId != null && storedSessionId.equals(sessionId);
    }

    /**
     * Получить количество устройств пользователя
     */
    public int getUserDeviceCount(String username) {
        return userPresenceService.getUserDeviceCount(username);
    }

    public List<User> getUserContacts(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return contactRepository.findContactsByUser(user);
    }

    public void addContact(String username, String contactUsername) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User contact = findByUsername(contactUsername)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        if (contactRepository.existsByUserAndContact(user, contact)) {
            throw new RuntimeException("Contact already exists");
        }

        Contact newContact = new Contact(user, contact);
        contactRepository.save(newContact);
    }

    public void removeContact(String username, String contactUsername) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User contact = findByUsername(contactUsername)
                .orElseThrow(() -> new RuntimeException("Contact not found"));

        contactRepository.deleteByUserAndContact(user, contact);
    }
}