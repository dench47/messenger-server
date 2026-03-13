package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserActivityService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserActivityController {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Autowired
    private UserService userService;

    @Autowired
    private UserActivityService userActivityService;

    private String getTimestamp() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    @PostMapping("/{username}/update-last-seen")
    public ResponseEntity<?> updateLastSeen(@PathVariable String username) {
        try {
            userService.updateLastSeen(username);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating last seen");
        }
    }

    @GetMapping("/{username}/last-seen")
    public ResponseEntity<LocalDateTime> getLastSeen(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(user.getLastSeen());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 👇 НОВЫЙ WEBSOCKET ENDPOINT
    @MessageMapping("/user/activity")
    public void updateUserActivity(@Payload Map<String, Object> activityData) {
        try {
            String username = (String) activityData.get("username");
            String activity = (String) activityData.get("activity");
            String chatPartner = (String) activityData.get("chatPartner");

            if (username == null) {
                System.err.println("[" + getTimestamp() + "] ❌ Username is null in activity update");
                return;
            }

            userActivityService.updateUserActivity(username, activity, chatPartner);

            System.out.println("[" + getTimestamp() + "] 👤 User activity updated: " + username +
                    " -> " + activity + ", partner: " + chatPartner);

        } catch (Exception e) {
            System.err.println("[" + getTimestamp() + "] ❌ Error updating user activity: " + e.getMessage());
            e.printStackTrace();
        }
    }
}