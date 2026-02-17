package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@CrossOrigin(origins = "*")
public class ContactController {

    @Autowired
    private UserService userService;

    // Получить список контактов
    @GetMapping
    public ResponseEntity<List<User>> getContacts(@RequestParam String username) {
        try {
            List<User> contacts = userService.getUserContacts(username);
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Добавить контакт
    @PostMapping("/add")
    public ResponseEntity<?> addContact(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String contactUsername = request.get("contactUsername");

            userService.addContact(username, contactUsername);
            return ResponseEntity.ok("Contact added");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Удалить контакт
    @DeleteMapping("/remove")
    public ResponseEntity<?> removeContact(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String contactUsername = request.get("contactUsername");

            userService.removeContact(username, contactUsername);
            return ResponseEntity.ok("Contact removed");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}