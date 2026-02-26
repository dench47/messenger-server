package com.messenger.messengerserver.repository;

import com.messenger.messengerserver.model.Message;
import com.messenger.messengerserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender = :user1 AND m.receiver = :user2) OR " +
            "(m.sender = :user2 AND m.receiver = :user1) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findConversation(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender.username = :username1 AND m.receiver.username = :username2) OR " +
            "(m.sender.username = :username2 AND m.receiver.username = :username1) " +
            "ORDER BY m.timestamp ASC")
    List<Message> findConversationByUsernames(@Param("username1") String username1,
                                              @Param("username2") String username2);

    List<Message> findByReceiverAndIsReadFalse(User receiver);

    @Query("SELECT m FROM Message m WHERE m.receiver.username = :username AND m.isRead = false")
    List<Message> findUnreadMessagesByUsername(@Param("username") String username);

    // 👇 НОВЫЙ МЕТОД - получает последнее сообщение между двумя пользователями
    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender.username = :username1 AND m.receiver.username = :username2) OR " +
            "(m.sender.username = :username2 AND m.receiver.username = :username1) " +
            "ORDER BY m.timestamp DESC LIMIT 1")
    Message findLastMessageBetweenUsers(@Param("username1") String username1,
                                        @Param("username2") String username2);
}