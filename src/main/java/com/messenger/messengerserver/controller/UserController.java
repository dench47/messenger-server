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
            List<String> onlineUsernames = userService.getOnlineUsers();

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(user -> {
                        boolean hasWebSocket = onlineUsernames.contains(user.getUsername());
                        boolean isActuallyActive = userService.isUserActuallyActive(user.getUsername());
                        return new UserWithStatusDTO(user, hasWebSocket, isActuallyActive);
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
            List<String> onlineUsernames = userService.getOnlineUsers();

            List<UserWithStatusDTO> usersWithStatus = users.stream()
                    .map(user -> {
                        boolean isActuallyOnline = onlineUsernames.contains(user.getUsername());
                        return new UserWithStatusDTO(user, isActuallyOnline);
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
}