package com.codogrammer.irangoldtracker.repository;

import com.codogrammer.irangoldtracker.entity.Alert;
import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUserIdOrderByIdAsc(Long userId);

    List<Alert> findAllByOrderByIdAsc();

    long countByUserId(Long userId);

    boolean existsByUserIdAndMarketAndItemNameIgnoreCase(Long userId, MarketCurrencies market, String itemName);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    /**
     * Returns how many rows this call actually removed, so a caller can tell whether it is the one
     * that claimed the alert.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Alert alert where alert.id = :id")
    int claim(@Param("id") Long id);
}
