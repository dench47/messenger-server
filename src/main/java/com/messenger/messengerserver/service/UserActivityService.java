package com.messenger.messengerserver.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserActivityService {

    // Храним текущую активность пользователя: username -> activity
    private final ConcurrentHashMap<String, String> userCurrentActivity = new ConcurrentHashMap<>();

    // Храним последнего собеседника: username -> chatPartner
    private final ConcurrentHashMap<String, String> userLastChatPartner = new ConcurrentHashMap<>();

    public void updateUserActivity(String username, String activity, String chatPartner) {
        userCurrentActivity.put(username, activity);
        if (chatPartner != null) {
            userLastChatPartner.put(username, chatPartner);
        }
        System.out.println("👤 [" + username + "] activity: " + activity + ", partner: " + chatPartner);
    }

    public void userDisconnected(String username) {
        userCurrentActivity.remove(username);
        userLastChatPartner.remove(username);
        System.out.println("👤 [" + username + "] disconnected, cleared activity");
    }

    public boolean isUserInChatWith(String username, String chatPartner) {
        String currentActivity = userCurrentActivity.get(username);
        String lastPartner = userLastChatPartner.get(username);

        boolean result = "ChatActivity".equals(currentActivity) && chatPartner.equals(lastPartner);
        System.out.println("🔍 Checking if " + username + " is in chat with " + chatPartner + ": " + result);
        return result;
    }
}