package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.dto.ContactDto;
import com.messenger.messengerserver.dto.UserWithStatusDTO;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserPresenceService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserPresenceService userPresenceService;

    // УЛУЧШЕННАЯ конвертация с деталями
    private UserWithStatusDTO convertUserToDTO(User user) {
        String username = user.getUsername();
        boolean isOnline = userService.isUserOnline(username);
        String lastSeenText;
        int deviceCount = userService.getUserDeviceCount(username);

        if (isOnline) {
            if (deviceCount > 1) {
                lastSeenText = "online (" + deviceCount + " устройства)";
            } else {
                lastSeenText = "online";
            }
        } else {
            // ВАЖНО: Используем getLastSeen() из user объекта, а не current time
            lastSeenText = UserService.formatLastSeenDetailed(user.getLastSeen());
        }

        UserWithStatusDTO dto = new UserWithStatusDTO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                isOnline ? "online" : "offline",
                lastSeenText
        );

        // Дополнительная информация для отладки
        System.out.println("👤 " + username + ": " +
                (isOnline ? "🟢" : "🔴") + " " +
                dto.getStatus() + " - " + lastSeenText);

        return dto;
    }

    @GetMapping
    public ResponseEntity<List<UserWithStatusDTO>> getUsers() {
        try {
            List<User> users = userService.getAllUsers();

            // Статистика перед отправкой
            int totalUsers = users.size();
            long onlineCount = users.stream()
                    .filter(user -> userService.isUserOnline(user.getUsername()))
                    .count();

            System.out.println("📊 User stats: " + totalUsers + " total, " +
                    onlineCount + " online, " +
                    (totalUsers - onlineCount) + " offline");

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(this::convertUserToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(usersWithStatus);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserWithStatusDTO>> searchUsers(@RequestParam String query) {
        try {
            List<User> users = userService.searchUsers(query);

            System.out.println("🔍 Search for '" + query + "': found " + users.size() + " users");

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(this::convertUserToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(usersWithStatus);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{username}")
    public ResponseEntity<User> getUser(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/online/count")
    public ResponseEntity<Map<String, Object>> getOnlineCount() {
        try {
            int onlineCount = userService.getOnlineUsersCount();
            int totalUsers = userService.getAllUsers().size();

            Map<String, Object> response = Map.of(
                    "onlineCount", onlineCount,
                    "totalUsers", totalUsers,
                    "timestamp", LocalDateTime.now().toString()
            );

            System.out.println("📊 Online count request: " + onlineCount + "/" + totalUsers);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/update-online-status")
    public ResponseEntity<Void> updateOnlineStatus(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            Boolean online = (Boolean) request.get("online");

            if (username == null || online == null) {
                return ResponseEntity.badRequest().build();
            }

            userService.updateUserInDatabase(username, online);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("❌ Error updating online status: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/update-fcm-token")
    public ResponseEntity<?> updateFcmToken(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String fcmToken = request.get("fcmToken");

            if (username == null || fcmToken == null) {
                return ResponseEntity.badRequest().body("Username and fcmToken are required");
            }

            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setFcmToken(fcmToken);
            userService.save(user);

            System.out.println("✅ FCM token updated for user: " + username);
            return ResponseEntity.ok("FCM token updated");

        } catch (Exception e) {
            System.err.println("❌ Error updating FCM token: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Error updating FCM token");
        }
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactDto>> getUserContacts(@RequestParam String username) {
        try {
            System.out.println("📡 Contacts request for user: " + username);

            List<User> contacts = userService.getUserContacts(username);

            // Обновляем статусы из Redis для каждого контакта
            for (User contact : contacts) {
                boolean isOnline = userPresenceService.isUserOnline(contact.getUsername());
                contact.setOnline(isOnline);
            }

            List<ContactDto> dtos = contacts.stream()
                    .map(ContactDto::new)
                    .collect(Collectors.toList());

            System.out.println("✅ Returning " + dtos.size() + " contacts");
            return ResponseEntity.ok(dtos);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}