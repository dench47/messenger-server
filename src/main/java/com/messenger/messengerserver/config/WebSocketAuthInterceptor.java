package com.messenger.messengerserver.config;

import com.messenger.messengerserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");

            String username = null;
            boolean isValidToken = false;

            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);

                if (authHeader.startsWith("Bearer ")) {
                    String jwt = authHeader.substring(7);
                    try {
                        // Проверяем валидность токена (включая expiration)
                        if (jwtUtil.validateToken(jwt)) {
                            username = jwtUtil.getUsernameFromToken(jwt);
                            isValidToken = true;
                            System.out.println("✅ WebSocket valid token for: " + username);
                        } else {
                            System.out.println("❌ WebSocket invalid token");
                            // Закрываем соединение при невалидном токене
                            throw new Exception("Invalid JWT token");
                        }
                    } catch (Exception e) {
                        System.out.println("WebSocket auth failed: " + e.getMessage());
                        // Закрываем соединение
                        throw new MessagingException("Authentication failed");
                    }
                }
            }

            // Устанавливаем аутентификацию только при валидном токене
            if (isValidToken && username != null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);

                    // Сохраняем username в атрибуты сессии
                    accessor.getSessionAttributes().put("username", username);

                    System.out.println("✅ WebSocket authenticated: " + username);
                } catch (Exception e) {
                    System.out.println("❌ WebSocket user details error: " + e.getMessage());
                    throw new MessagingException("User not found");
                }
            } else {
                System.out.println("❌ WebSocket connection rejected - no valid token");
                throw new MessagingException("No valid authentication");
            }
        }
        return message;
    }
}