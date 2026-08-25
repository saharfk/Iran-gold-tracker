package com.codogrammer.irangoldtracker.repository;

import com.codogrammer.irangoldtracker.entity.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
}
