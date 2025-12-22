package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.dto.AuthRequest;
import com.messenger.messengerserver.dto.AuthResponse;
import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.UserService;
import com.messenger.messengerserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder; // Добавьте это поле

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            String displayName = request.get("displayName");

            System.out.println("🔵 [REGISTER] Attempting to register user: " + username);

            // Проверка обязательных полей
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username is required");
            }
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password is required");
            }

            // Проверяем существует ли пользователь
            if (userService.findByUsername(username).isPresent()) {
                System.out.println("❌ [REGISTER] User already exists: " + username);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("User '" + username + "' already exists");
            }

            // Создаем нового пользователя с зашифрованным паролем
            String encodedPassword = passwordEncoder.encode(password);
            User newUser = new User(username, encodedPassword);

            // Устанавливаем displayName
            if (displayName != null && !displayName.trim().isEmpty()) {
                newUser.setDisplayName(displayName);
            } else {
                newUser.setDisplayName(username);
            }

            // Сохраняем пользователя
            userService.saveUser(newUser);

            System.out.println("✅ [REGISTER] User created successfully: " + username);

            // Возвращаем успешный ответ
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "User registered successfully",
                            "username", username,
                            "displayName", newUser.getDisplayName()
                    ));

        } catch (Exception e) {
            System.err.println("❌ [REGISTER] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String accessToken = jwtUtil.generateAccessToken(authRequest.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(authRequest.getUsername());

            // Получаем пользователя для displayName
            User user = userService.findByUsername(authRequest.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Обновляем last_seen и online статус
            userService.setUserOnline(authRequest.getUsername());

            AuthResponse authResponse = new AuthResponse(
                    accessToken,
                    refreshToken,
                    jwtUtil.getAccessTokenExpiration(),
                    authRequest.getUsername(),
                    user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
            );

            return ResponseEntity.ok(authResponse);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверные учетные данные");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка сервера: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        try {
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            // Получаем пользователя для displayName
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String newAccessToken = jwtUtil.generateAccessToken(username);
            String newRefreshToken = jwtUtil.generateRefreshToken(username);

            AuthResponse authResponse = new AuthResponse(
                    newAccessToken,
                    newRefreshToken,
                    jwtUtil.getAccessTokenExpiration(),
                    username,
                    user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
            );

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token refresh failed");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        String username = request.get("username");

        // ДОБАВИМ ОТЛАДКУ
        System.out.println("🔴🔴🔴 LOGOUT ENDPOINT CALLED!");
        System.out.println("🔴🔴🔴 Username: " + username);
        System.out.println("🔴🔴🔴 Request: " + request);
        System.out.println("🔴🔴🔴 Stack trace:");
        if (username != null) {
            userService.setUserOffline(username);

            // Принудительно разрываем все WebSocket сессии пользователя
            userService.forceDisconnectUser(username);

            System.out.println("🔴 User logged out and disconnected: " + username);
        }
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/remove-fcm-token")
    public ResponseEntity<?> removeFcmToken(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");

            if (username == null) {
                return ResponseEntity.badRequest().body("Username is required");
            }

            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setFcmToken(null);
            userService.save(user);

            System.out.println("🗑️ FCM token removed for user: " + username);
            return ResponseEntity.ok("FCM token removed");

        } catch (Exception e) {
            System.err.println("❌ Error removing FCM token: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Error removing FCM token");
        }
    }

    @PostMapping("/refresh-long")
    public ResponseEntity<?> refreshLongToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || !jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        try {
            String username = jwtUtil.getUsernameFromToken(refreshToken);
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ВАЖНО: Генерируем НОВЫЙ refresh token с таким же сроком
            String newAccessToken = jwtUtil.generateAccessToken(username);
            String newRefreshToken = jwtUtil.generateRefreshToken(username); // ← НОВЫЙ refresh!

            AuthResponse authResponse = new AuthResponse(
                    newAccessToken,
                    newRefreshToken, // Отправляем новый refresh token клиенту
                    jwtUtil.getAccessTokenExpiration(),
                    username,
                    user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
            );

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token refresh failed");
        }
    }
}