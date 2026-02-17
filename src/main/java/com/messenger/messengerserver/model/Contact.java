package com.messenger.messengerserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore  // ← ТОЖЕ ДОБАВИТЬ (если не нужно при возврате контактов)

    private User user;

    @ManyToOne
    @JoinColumn(name = "contact_id", nullable = false)
    private User contact;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Конструкторы
    public Contact() {
        this.createdAt = LocalDateTime.now();
    }

    public Contact(User user, User contact) {
        this();
        this.user = user;
        this.contact = contact;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public User getContact() { return contact; }
    public void setContact(User contact) { this.contact = contact; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}