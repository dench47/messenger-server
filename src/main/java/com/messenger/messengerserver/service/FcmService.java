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

    public void sendNewMessageNotification(String senderUsername, String receiverUsername, String messageContent, Long messageId) {
        try {
            System.out.println("=== 🔵 [FCM TRACE] START ===");
            System.out.println("  Sender: " + senderUsername);
            System.out.println("  Receiver: " + receiverUsername);
            System.out.println("  Message ID: " + messageId);

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

            // 2. Получаем отправителя для display name
            User sender = userService.findByUsername(senderUsername).orElse(null);
            String senderDisplayName = sender != null && sender.getDisplayName() != null
                    ? sender.getDisplayName()
                    : senderUsername;

            // 3. Создаем уведомление
            System.out.println("  🔍 Creating notification...");

            Notification notification = Notification.builder()
                    .setTitle(senderDisplayName)
                    .setBody(messageContent)
                    .build();

            // 4. Создаем сообщение с ПОЛНЫМИ данными
            System.out.println("  🔍 Building FCM message...");

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putData("type", "NEW_MESSAGE")
                    .putData("sender", senderDisplayName)
                    .putData("senderUsername", senderUsername)
                    .putData("message", messageContent)
                    .putData("messageId", messageId != null ? messageId.toString() : "0")
                    .putData("deepLinkAction", "OPEN_CHAT")        // ← НОВОЕ
                    .putData("targetUsername", senderUsername)     // ← НОВОЕ
                    .build();

            System.out.println("📤 [FCM DEBUG] Sending data:");
            System.out.println("   senderUsername: " + senderUsername);
            System.out.println("   messageId: " + messageId);

            // 5. Отправляем
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

    public void sendIncomingCallNotification(String callerUsername, String receiverUsername) {
        try {
            System.out.println("📞 [FCM CALL] Sending incoming call notification");
            System.out.println("  Caller: " + callerUsername);
            System.out.println("  Receiver: " + receiverUsername);

            // 1. Получаем получателя и его FCM токен
            User receiver = userService.findByUsername(receiverUsername)
                    .orElseThrow(() -> new RuntimeException("Receiver not found for call"));

            String fcmToken = receiver.getFcmToken();

            if (fcmToken == null || fcmToken.isEmpty()) {
                System.out.println("⚠️ No FCM token for user: " + receiverUsername);
                return;
            }

            // 2. Получаем информацию о звонящем
            User caller = userService.findByUsername(callerUsername).orElse(null);
            String callerDisplayName = caller != null && caller.getDisplayName() != null
                    ? caller.getDisplayName()
                    : callerUsername;

            // 3. Создаем уведомление о звонке
            Notification notification = Notification.builder()
                    .setTitle("Входящий звонок")
                    .setBody(callerDisplayName + " звонит вам")
                    .build();

            // 4. Создаем сообщение с данными звонка
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putData("type", "INCOMING_CALL")
                    .putData("caller", callerDisplayName)
                    .putData("callerUsername", callerUsername)
                    .putData("callType", "audio") // или "video", можно передавать как параметр
                    .putData("deepLinkAction", "ANSWER_CALL")
                    .putData("targetUsername", receiverUsername)
                    .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();

            // 5. Отправляем
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM call notification sent: " + response);

        } catch (Exception e) {
            System.err.println("❌ Error sending FCM call notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}