package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<User>> getUsers() {
        try {
            // Используем метод с реальным онлайн статусом
            List<User> users = userService.getUsersWithRealOnlineStatus();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String query) {
        try {
            List<User> users = userService.searchUsers(query);
            // Обновляем онлайн статусы для найденных пользователей
            List<String> onlineUsernames = userService.getOnlineUsers();
            List<User> usersWithStatus = users.stream()
                    .map(user -> {
                        user.setOnline(onlineUsernames.contains(user.getUsername()));
                        return user;
                    })
                    .toList();
            return ResponseEntity.ok(usersWithStatus);
        } catch (Exception e) {
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
}