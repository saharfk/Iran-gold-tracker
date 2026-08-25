package com.codogrammer.irangoldtracker.entity;

import com.codogrammer.irangoldtracker.utils.MarketCurrencies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "alert",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alert_user_item",
                columnNames = {"user_id", "market", "item_name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private TelegramUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketCurrencies market;

    @Column(nullable = false)
    private String itemName;

    private String symbol;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal fromPrice;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal toPrice;

    @Column(nullable = false)
    private Instant createdAt;

    public Alert(
            TelegramUser user,
            MarketCurrencies market,
            String itemName,
            String symbol,
            BigDecimal fromPrice,
            BigDecimal toPrice
    ) {
        this.user = user;
        this.market = market;
        this.itemName = itemName;
        this.symbol = symbol;
        this.fromPrice = fromPrice;
        this.toPrice = toPrice;
        this.createdAt = Instant.now();
    }
}
