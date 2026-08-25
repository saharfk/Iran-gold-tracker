package com.codogrammer.irangoldtracker.repository;

import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserIdOrderByIdAsc(Long userId);

    List<Alert> findAllByOrderByIdAsc();

    long countByUserId(Long userId);

    boolean existsByUserIdAndMarketAndItemNameIgnoreCase(Long userId, MarketCurrencies market, String itemName);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);
}
