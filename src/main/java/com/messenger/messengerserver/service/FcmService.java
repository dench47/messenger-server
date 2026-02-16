package com.messenger.messengerserver.service;

import com.google.firebase.messaging.*;
import com.messenger.messengerserver.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

            User sender = userService.findByUsername(senderUsername).orElse(null);
            String senderDisplayName = sender != null && sender.getDisplayName() != null
                    ? sender.getDisplayName()
                    : senderUsername;

            System.out.println("  🔍 Building FCM message...");

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putData("type", "NEW_MESSAGE")
                    .putData("sender", senderDisplayName)
                    .putData("senderUsername", senderUsername)
                    .putData("message", messageContent)
                    .putData("messageId", messageId != null ? messageId.toString() : "0")
                    .putData("deepLinkAction", "OPEN_CHAT")
                    .putData("targetUsername", senderUsername)
                    .build();

            System.out.println("📤 [FCM DEBUG] Sending data:");
            System.out.println("   senderUsername: " + senderUsername);
            System.out.println("   messageId: " + messageId);

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

            User receiver = userService.findByUsername(receiverUsername)
                    .orElseThrow(() -> new RuntimeException("Receiver not found for call"));

            String fcmToken = receiver.getFcmToken();

            if (fcmToken == null || fcmToken.isEmpty()) {
                System.out.println("⚠️ No FCM token for user: " + receiverUsername);
                return;
            }

            User caller = userService.findByUsername(callerUsername).orElse(null);
            String callerDisplayName = caller != null && caller.getDisplayName() != null
                    ? caller.getDisplayName()
                    : callerUsername;

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle("Входящий звонок")
                            .setBody(callerDisplayName + " звонит вам")
                            .build())
                    .putData("type", "INCOMING_CALL")
                    .putData("caller", callerDisplayName)
                    .putData("callerUsername", callerUsername)
                    .putData("callType", "audio")
                    .putData("deepLinkAction", "ANSWER_CALL")
                    .putData("targetUsername", receiverUsername)
                    .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM call notification sent: " + response);

        } catch (Exception e) {
            System.err.println("❌ Error sending FCM call notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // НОВЫЙ МЕТОД ДЛЯ ИНДИВИДУАЛЬНОЙ ОТПРАВКИ
    public void sendReconnectCommand(String username) {
        try {
            System.out.println("=== 🔵 [FCM RECONNECT] START ===");
            System.out.println("  Target user: " + username);

            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String fcmToken = user.getFcmToken();
            System.out.println("  🔍 FCM Token from DB: " +
                    (fcmToken != null ? "'" + fcmToken.substring(0, Math.min(10, fcmToken.length())) + "...'" : "NULL"));

            if (fcmToken == null || fcmToken.isEmpty()) {
                System.out.println("⚠️ No FCM token for user: " + username);
                return;
            }

            System.out.println("  🔍 Building reconnect FCM message...");

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putData("type", "SERVER_RESTARTED")
                    .putData("action", "DO_BACKGROUND")
                    .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                    .build();

            System.out.println("  🔍 Sending via FirebaseMessaging...");
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("✅ FCM reconnect command sent: " + response);

        } catch (Exception e) {
            System.err.println("❌ Error sending FCM reconnect command: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("=== 🔵 [FCM RECONNECT] END ===");
        }
    }

    // НОВЫЙ МЕТОД ДЛЯ BATCH ОТПРАВКИ (5000+)
    public void sendReconnectCommandBatch(List<String> usernames) {
        try {
            System.out.println("📱 Отправка FCM команд " + usernames.size() + " пользователям");

            for (String username : usernames) {
                User user = userService.findByUsername(username).orElse(null);
                if (user != null && user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {

                    Message message = Message.builder()
                            .setToken(user.getFcmToken())
                            .putData("type", "SERVER_RESTARTED")
                            .putData("action", "DO_BACKGROUND")
                            .putData("timestamp", String.valueOf(System.currentTimeMillis()))
                            .build();

                    FirebaseMessaging.getInstance().send(message);
                    System.out.println("  ✅ Отправлено: " + username);
                    Thread.sleep(100); // небольшая задержка
                }
            }
            System.out.println("✅ Отправка завершена");
        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
        }
    }
}