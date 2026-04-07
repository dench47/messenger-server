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

    @Autowired
    private UserActivityService userActivityService;

    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> internalToRabbitSessionMap = new ConcurrentHashMap<>();

    public void setRabbitSessionId(String internalSessionId, String rabbitSessionId) {
        internalToRabbitSessionMap.put(internalSessionId, rabbitSessionId);
        System.out.printf("[SESSION] 💾 Сохранено соответствие: %s -> %s%n",
                internalSessionId, rabbitSessionId);
    }

    public void removeRabbitSessionId(String internalSessionId) {
        internalToRabbitSessionMap.remove(internalSessionId);
    }

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

        sendImmediateStatusUpdate(username, false);
        System.out.println("⏰ Last seen updated for " + username + ": " + user.getLastSeen());
    }

    public void terminateOtherSessions(String username, String currentInternalSessionId) {
        String oldInternalSessionId = userPresenceService.getUserSession(username);
        if (oldInternalSessionId != null && !oldInternalSessionId.equals(currentInternalSessionId)) {
            System.out.printf("[SESSION] 🔒 Завершаем старую сессию для %s: %s%n",
                    username, oldInternalSessionId);

            String oldRabbitSessionId = internalToRabbitSessionMap.get(oldInternalSessionId);

            Map<String, Object> logoutMessage = new HashMap<>();
            logoutMessage.put("type", "SESSION_TERMINATED");
            logoutMessage.put("message", "Вы вошли на другом устройстве. Сессия завершена.");
            logoutMessage.put("timestamp", System.currentTimeMillis());
            logoutMessage.put("targetSessionId", oldRabbitSessionId != null ? oldRabbitSessionId : oldInternalSessionId);

            messagingTemplate.convertAndSend("/topic/session", logoutMessage);

            System.out.printf("[SESSION] 📤 Отправлено уведомление в /topic/session для RabbitMQ сессии %s%n",
                    oldRabbitSessionId != null ? oldRabbitSessionId : oldInternalSessionId);
        }
    }

    public void userConnected(String username, String internalSessionId, String rabbitSessionId) {
        System.out.printf("[SESSION] 🔗 userConnected: %s, internalSessionId: %s, rabbitSessionId: %s%n",
                username, internalSessionId, rabbitSessionId);

        setRabbitSessionId(internalSessionId, rabbitSessionId);
        terminateOtherSessions(username, internalSessionId);

        userPresenceService.userConnected(username, internalSessionId);
        userSessionMap.put(username, internalSessionId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setOnline(true);
        user.setLastSeen(null);
        userRepository.save(user);

        System.out.println("👤 " + username + ": 🟢 CONNECTED");

        sendImmediateStatusUpdate(username, true);
        System.out.println("✅ User connected: " + username);
    }

    public void userDisconnected(String username, String internalSessionId) {
        try {
            Thread.sleep(50);

            removeRabbitSessionId(internalSessionId);
            userPresenceService.userDisconnected(username, internalSessionId);
            userSessionMap.remove(username);

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setOnline(false);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);

            System.out.println("👤 " + username + ": 🔴 DISCONNECTED (internalSession: " + internalSessionId +
                    ", last seen: " + formatLastSeenDetailed(user.getLastSeen()) + ")");

            new Thread(() -> {
                try {
                    Thread.sleep(100);
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
        return userPresenceService.isUserOnline(username);
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(userPresenceService.getOnlineUsers());
    }

    public int getOnlineUsersCount() {
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
        boolean hasWebSocket = userPresenceService.isUserOnline(username);

        String status = hasWebSocket ? "online" : "offline";
        String lastSeenText = hasWebSocket ? "online" : formatLastSeenDetailed(user.getLastSeen());

        Map<String, Object> statusData = new HashMap<>();
        statusData.put("type", "USER_STATUS_UPDATE");
        statusData.put("username", username);
        statusData.put("online", hasWebSocket);
        statusData.put("status", status);
        statusData.put("lastSeenText", lastSeenText);

        return statusData;
    }

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

    public String getUserSessionId(String username) {
        return userSessionMap.get(username);
    }

    public boolean hasUserSession(String username, String sessionId) {
        String storedSessionId = userSessionMap.get(username);
        return storedSessionId != null && storedSessionId.equals(sessionId);
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

    public void updateAvatarUrl(String username, String avatarUrl) {
        User user = findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    public boolean isUserInChatWith(String username, String chatPartner) {
        return userActivityService.isUserInChatWith(username, chatPartner);
    }
}