package com.messenger.messengerserver.controller;

import com.messenger.messengerserver.service.FcmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private FcmService fcmService;

    @PostMapping("/send-fcm")
    public String testFcm(@RequestParam String receiverUsername) {
        System.out.println("🧪 TEST FCM для пользователя: " + receiverUsername);
        fcmService.sendNewMessageNotification(
                "TEST_SENDER",
                receiverUsername,
                "Это тестовое сообщение через FCM"
        );
        return "FCM test executed, check server logs";
    }
}