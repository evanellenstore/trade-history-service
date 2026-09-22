package com.trade.history.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_indicators_backtest")
@Getter
@NoArgsConstructor
public class BacktestMarketIndicator {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candle_id")
    private Candle candle;

    private String symbol;
    private String symbolToken;
    private String timeframe;

    @Column(name = "candle_time")
    private LocalDateTime candleTime;

    private Double trend_ema20;
    private Double trend_ema50;
    private Double trend_ema100;
    private Double trend_ema200;
    private Double trend_adx;
    private Double trend_plusDi;
    private Double trend_minusDi;
    private Double momentum_rsi14;
    private Double momentum_macd;
    private Double momentum_macdSignal;
    private Double momentum_macdHistogram;
    private Double momentum_stochasticK;
    private Double momentum_stochasticD;
    private Double momentum_cci;
    private Double volume_vwap;
    private Double volume_mfi;
    private Double volume_cmf;
    private Double volatility_atr;
    private Double volatility_bbWidth;
}
