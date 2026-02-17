package com.messenger.messengerserver.repository;

import com.messenger.messengerserver.model.Contact;
import com.messenger.messengerserver.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    // Получить всех контактов пользователя
    @Query("SELECT c.contact FROM Contact c WHERE c.user = :user")
    List<User> findContactsByUser(@Param("user") User user);

    // Проверить, есть ли уже контакт
    boolean existsByUserAndContact(User user, User contact);

    // Удалить контакт
    void deleteByUserAndContact(User user, User contact);
}