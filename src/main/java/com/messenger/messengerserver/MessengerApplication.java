package com.messenger.messengerserver;

import com.messenger.messengerserver.config.ShutdownMemory;
import com.messenger.messengerserver.service.UserPresenceService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class MessengerApplication {

    @Autowired
    private UserPresenceService userPresenceService; // ← ЭТО БЫЛО ПРОПУЩЕНО!

    public static void main(String[] args) {
        SpringApplication.run(MessengerApplication.class, args);
    }

    @PostConstruct
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🔥 Сохраняю онлайн пользователей...");
            List<String> onlineUsers = userPresenceService.getAllOnlineUsers();
            ShutdownMemory.save(onlineUsers);
        }));
    }
}