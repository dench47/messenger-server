package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserActivityController {

    @Autowired
    private UserService userService;

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
}