package com.trade.history.repository;

import com.trade.history.entity.TrainingDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TrainingDatasetRepository extends JpaRepository<TrainingDataset, Long> {
    boolean existsBySymbolTokenAndTimeframeAndCandleTime(String symbolToken, String timeframe,
                                                          LocalDateTime candleTime);

    @Query("select d.candleTime from TrainingDataset d where d.symbolToken = :symbolToken "
            + "and d.timeframe = :timeframe and d.candleTime in :candleTimes")
    List<LocalDateTime> findExistingCandleTimes(@Param("symbolToken") String symbolToken,
                                                 @Param("timeframe") String timeframe,
                                                 @Param("candleTimes") Collection<LocalDateTime> candleTimes);
}
