package com.messenger.messengerserver.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileService {

    private final String uploadDir = "uploads/avatars/";

    public String saveAvatar(MultipartFile file, String username) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Удаляем старые аватарки этого пользователя
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadPath, username + "_*")) {
            for (Path oldFile : stream) {
                Files.delete(oldFile);
                System.out.println("🗑️ Deleted old avatar: " + oldFile.getFileName());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error deleting old avatars: " + e.getMessage());
        }

        String fileName = username + "_" + UUID.randomUUID().toString() + ".jpg";
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        // Возвращаем URL для доступа
        return "/uploads/avatars/" + fileName;
    }
}