package com.codogrammer.irangoldtracker.repository;

import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.entity.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserIdAndStatusOrderByIdAsc(Long userId, AlertStatus status);

    List<Alert> findByStatusOrderByIdAsc(AlertStatus status);

    long countByUserIdAndStatus(Long userId, AlertStatus status);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);
}
