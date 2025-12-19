package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.dto.UserWithStatusDTO;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<UserWithStatusDTO>> getUsers() {
        try {
            List<User> users = userService.getAllUsers();

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(user -> {
                        // Используем методы сервиса
                        String status = userService.determineUserStatus(user.getUsername());
                        String lastSeenText = userService.formatTimeAgo(user.getLastSeen());

                        // Создаем DTO с готовыми значениями
                        return new UserWithStatusDTO(
                                user.getId(),
                                user.getUsername(),
                                user.getDisplayName(),
                                user.getAvatarUrl(),
                                status,
                                lastSeenText
                        );
                    })
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

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(user -> {
                        // Используем те же методы сервиса
                        String status = userService.determineUserStatus(user.getUsername());
                        String lastSeenText = userService.formatTimeAgo(user.getLastSeen());

                        return new UserWithStatusDTO(
                                user.getId(),
                                user.getUsername(),
                                user.getDisplayName(),
                                user.getAvatarUrl(),
                                status,
                                lastSeenText
                        );
                    })
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

            // Обновляем онлайн статус
            boolean isOnline = userService.isUserOnline(username);
            user.setOnline(isOnline);

            return ResponseEntity.ok(user);
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

            userService.updateUserOnlineStatus(username, online);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/update-activity")
    public ResponseEntity<Void> updateActivity(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            if (username == null) {
                return ResponseEntity.badRequest().build();
            }

            userService.updateUserActivity(username);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
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
            userService.save(user); // Нужно добавить метод save в UserService

            System.out.println("✅ FCM token updated for user: " + username);
            return ResponseEntity.ok("FCM token updated");

        } catch (Exception e) {
            System.err.println("❌ Error updating FCM token: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Error updating FCM token");
        }
    }


}