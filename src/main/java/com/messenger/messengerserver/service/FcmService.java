package com.messenger.messengerserver.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.messenger.messengerserver.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

    @Autowired
    private UserService userService;

    public void sendNewMessageNotification(String senderUsername, String receiverUsername, String messageContent) {
        try {
            System.out.println("=== 🔵 [FCM TRACE] START ===");
            System.out.println("  Sender: " + senderUsername);
            System.out.println("  Receiver: " + receiverUsername);

            // 1. Получаем получателя и его FCM токен
            System.out.println("  🔍 Looking for receiver in DB...");
            User receiver = userService.findByUsername(receiverUsername)
                    .orElseThrow(() -> new RuntimeException("Receiver not found"));
            System.out.println("  ✅ Receiver found: " + receiver.getUsername());


            String fcmToken = receiver.getFcmToken();
            System.out.println("  🔍 FCM Token from DB: " +
                    (fcmToken != null ? "'" + fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...'" : "NULL"));

            if (fcmToken == null || fcmToken.isEmpty()) {
                System.out.println("⚠️ No FCM token for user: " + receiverUsername);
                return;
            }

            // 2. Создаем уведомление
            System.out.println("  🔍 Creating notification...");

            Notification notification = Notification.builder()
                    .setTitle(senderUsername)
                    .setBody(messageContent)
                    .build();

            // 3. Создаем сообщение
            System.out.println("  🔍 Building FCM message...");

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putData("type", "NEW_MESSAGE")
                    .putData("sender", senderUsername)
                    .putData("message", messageContent)
                    .build();

            // 4. Отправляем
            System.out.println("  🔍 Sending via FirebaseMessaging...");

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM notification sent: " + response);

        } catch (Exception e) {
            System.err.println("❌ Error sending FCM notification: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("=== 🔵 [FCM TRACE] END ===");
        }
    }
}