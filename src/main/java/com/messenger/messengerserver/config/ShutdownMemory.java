package com.messenger.messengerserver.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ShutdownMemory {
    private static final Path FILE_PATH = Paths.get("online_users.txt");

    public static void save(List<String> users) {
        try {
            Files.write(FILE_PATH, users);
            System.out.println("💾 Сохранено в файл: " + users.size() + " пользователей");
        } catch (Exception e) {
            System.err.println("❌ Ошибка сохранения: " + e.getMessage());
        }
    }

    public static List<String> load() {
        try {
            if (Files.exists(FILE_PATH)) {
                List<String> users = Files.readAllLines(FILE_PATH);
                Files.delete(FILE_PATH); // Удаляем после загрузки
                System.out.println("📂 Загружено из файла: " + users.size() + " пользователей");
                return users;
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки: " + e.getMessage());
        }
        return new ArrayList<>();
    }
}