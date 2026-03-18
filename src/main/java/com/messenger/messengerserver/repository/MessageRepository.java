package com.messenger.messengerserver.repository;

import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m " +
            "WHERE (m.sender.username = :user1 AND m.receiver.username = :user2) " +
            "OR (m.sender.username = :user2 AND m.receiver.username = :user1) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findConversationByUsernames(@Param("user1") String user1,
                                              @Param("user2") String user2);

    @Query("SELECT m FROM Message m " +
            "WHERE m.receiver.username = :username AND m.isRead = false " +
            "ORDER BY m.timestamp DESC")
    List<Message> findUnreadMessagesByUsername(@Param("username") String username);

    @Query("SELECT m FROM Message m " +
            "WHERE (m.sender.username = :user1 AND m.receiver.username = :user2) " +
            "OR (m.sender.username = :user2 AND m.receiver.username = :user1) " +
            "ORDER BY m.timestamp DESC LIMIT 1")
    Message findLastMessageBetweenUsers(@Param("user1") String user1,
                                        @Param("user2") String user2);

    // 👇 Загружает сообщение с пользователями (JOIN FETCH)
    @Query("SELECT m FROM Message m " +
            "JOIN FETCH m.sender " +
            "JOIN FETCH m.receiver " +
            "WHERE m.id = :messageId")
    Optional<Message> findByIdWithUsers(@Param("messageId") Long messageId);

    // 👇 НОВЫЙ МЕТОД: найти сообщения по получателю и статусу
    @Query("SELECT m FROM Message m " +
            "WHERE m.receiver.username = :username AND m.status = :status " +
            "ORDER BY m.timestamp ASC")
    List<Message> findByReceiverUsernameAndStatus(@Param("username") String username,
                                                  @Param("status") MessageStatus status);
}