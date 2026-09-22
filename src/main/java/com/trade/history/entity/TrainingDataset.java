package com.trade.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "training_dataset", indexes = {
        @Index(name = "idx_training_dataset_symbol_timeframe_candle_time",
                columnList = "symbolToken,timeframe,candle_time")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_training_dataset_symbol_timeframe_candle",
                columnNames = {"symbolToken", "timeframe", "candle_time"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingDataset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String symbolToken;
    private String timeframe;

    @Column(name = "candle_time", nullable = false)
    private LocalDateTime candleTime;

    private Double rsi14;
    private Double adx;
    private Double plusDi;
    private Double minusDi;
    private Double macd;
    private Double macdSignal;
    private Double macdHistogram;
    private Double ema20;
    private Double ema50;
    private Double ema100;
    private Double ema200;
    private Double atr;
    private Double vwap;
    private Double stochasticK;
    private Double stochasticD;
    private Double cci;
    private Double mfi;
    private Double cmf;
    private Double bbWidth;

    @Column(name = "close_price", nullable = false)
    private Double closePrice;
    private Double futureClosePrice;
    private Double futureReturnPct;
    private Integer target;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
