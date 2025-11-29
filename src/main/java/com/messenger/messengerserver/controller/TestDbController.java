package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestDbController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/api/test/db")
    public String testDatabase() {
        return "Database test endpoint is working!";
    }

    @GetMapping("/api/test/create-user")
    public String testCreateUser() {
        try {
            // Проверяем создание пользователя
            if (!userRepository.existsByUsername("testuser")) {
                User user = new User("testuser", "password");
                user.setDisplayName("Test User"); // Устанавливаем displayName отдельно
                userRepository.save(user);
                return "Test user created successfully!";
            }
            return "Test user already exists!";
        } catch (Exception e) {
            return "Database error: " + e.getMessage();
        }
    }

    @GetMapping("/api/test/users-count")
    public String getUsersCount() {
        try {
            long count = userRepository.count();
            return "Total users in database: " + count;
        } catch (Exception e) {
            return "Database error: " + e.getMessage();
        }
    }

    @GetMapping("/api/test/online-status")
    public String testOnlineStatus() {
        try {
            // Проверяем работу онлайн статусов
            long totalUsers = userRepository.count();
            long onlineUsers = userRepository.findAll().stream()
                    .filter(User::getOnline)
                    .count();

            return String.format("Users: total=%d, online=%d, offline=%d",
                    totalUsers, onlineUsers, totalUsers - onlineUsers);
        } catch (Exception e) {
            return "Error checking online status: " + e.getMessage();
        }
    }
}