package com.codogrammer.irangoldtracker.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "telegram_user")
@Getter
@Setter
@NoArgsConstructor
public class TelegramUser {

    @Id
    private Long id;

    @Column(nullable = false)
    private Long chatId;

    private String firstName;

    private String username;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alert> alerts = new ArrayList<>();

    public TelegramUser(Long id, Long chatId, String firstName, String username) {
        this.id = id;
        this.chatId = chatId;
        this.firstName = firstName;
        this.username = username;
        this.createdAt = Instant.now();
    }
}
