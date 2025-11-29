package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class OnlineUsersController {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // WebSocket endpoint для запроса онлайн пользователей
    @MessageMapping("/users/online")
    public void getOnlineUsers() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            messagingTemplate.convertAndSend("/topic/online.users", onlineUsers);
            System.out.println("📢 Sent online users via WebSocket: " + onlineUsers.size() + " users");
        } catch (Exception e) {
            System.err.println("❌ Error sending online users: " + e.getMessage());
        }
    }

    // REST endpoint для проверки онлайн статуса конкретного пользователя
    @GetMapping("/{username}/online")
    public ResponseEntity<Boolean> isUserOnline(@PathVariable String username) {
        try {
            boolean isOnline = userService.isUserOnline(username);
            return ResponseEntity.ok(isOnline);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // REST endpoint для получения списка онлайн пользователей
    @GetMapping("/online")
    public ResponseEntity<List<String>> getOnlineUsersList() {
        try {
            List<String> onlineUsers = userService.getOnlineUsers();
            return ResponseEntity.ok(onlineUsers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // REST endpoint для получения всех пользователей с реальным онлайн статусом
    @GetMapping("/with-status")
    public ResponseEntity<List<com.messenger.messengerserver.model.User>> getUsersWithOnlineStatus() {
        try {
            List<com.messenger.messengerserver.model.User> users = userService.getUsersWithRealOnlineStatus();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}