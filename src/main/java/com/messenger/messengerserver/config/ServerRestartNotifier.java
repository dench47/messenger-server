package com.messenger.messengerserver.config;

import com.messenger.messengerserver.model.User;
import com.messenger.messengerserver.service.FcmService;
import com.messenger.messengerserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServerRestartNotifier {

    @Autowired
    private FcmService fcmService;

    @EventListener(ApplicationReadyEvent.class)
    public void onServerStart() throws Exception {
//        Thread.sleep(5000);
        List<String> onlineUsers = ShutdownMemory.load();
        if (!onlineUsers.isEmpty()) {
            fcmService.sendReconnectCommandBatch(onlineUsers);
        }
    }
}