package com.messenger.messengerserver.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // RabbitMQ как внешний STOMP брокер
        config.enableStompBrokerRelay("/topic", "/queue", "/exchange")
                .setRelayHost("localhost")           // RabbitMQ хост
                .setRelayPort(61613)                // STOMP порт RabbitMQ
                .setClientLogin("guest")            // RabbitMQ логин
                .setClientPasscode("guest")         // RabbitMQ пароль
                .setSystemLogin("guest")            // Системный логин
                .setSystemPasscode("guest")         // Системный пароль
                .setVirtualHost("/")                // Виртуальный хост
                .setAutoStartup(true);              // Автозапуск

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");

        System.out.println("✅ RabbitMQ STOMP broker configured for production (без heartbeat)");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(new CustomHandshakeHandler());

        // SockJS fallback endpoint
        registry.addEndpoint("/ws/sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setHeartbeatTime(25000); // SockJS heartbeat (не STOMP!)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        // Увеличиваем размер пула для обработки 5000+ соединений
        registration.taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(20)
                .queueCapacity(1000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Увеличиваем размер пула для исходящих сообщений
        registration.taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(20)
                .queueCapacity(1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Настройки WebSocket транспорта
        registration.setMessageSizeLimit(128 * 1024)      // 128KB
                .setSendBufferSizeLimit(512 * 1024)   // 512KB
                .setSendTimeLimit(20000)              // 20 секунд
                .setTimeToFirstMessage(30000);        // 30 секунд до первого сообщения
    }
}