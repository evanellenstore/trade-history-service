package com.trade.history.repository;

import com.trade.history.entity.BacktestMarketIndicator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestMarketIndicatorRepository extends JpaRepository<BacktestMarketIndicator, Long> {
    Page<BacktestMarketIndicator> findBySymbolTokenAndTimeframeOrderByCandleTimeAsc(
            String symbolToken, String timeframe, Pageable pageable);
}
