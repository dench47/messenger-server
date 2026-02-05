package com.messenger.messengerserver.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // Principal уже установлен в WebSocketAuthInterceptor
        // Здесь просто возвращаем его из атрибутов
        return (Principal) attributes.get("PRINCIPAL");
    }
}