package com.trade.history.repository;

import com.trade.history.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    interface BackfillStatus {
        String getSymbolToken();

        Long getCandleCount();

        LocalDateTime getUpdatedAt();
    }
    
    /**
     * Find candles by symbol and timeframe
     */
    List<Candle> findBySymbolAndTimeframeOrderByCandleTimeDesc(String symbol, String timeframe);
    
    /**
     * Find candles within time range
     */
    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe " +
           "AND c.candleTime >= :startTime AND c.candleTime <= :endTime ORDER BY c.candleTime ASC")
    List<Candle> findCandlesInTimeRange(@Param("symbol") String symbol, 
                                       @Param("timeframe") String timeframe,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find latest N candles for a symbol
     */
    List<Candle> findTop500BySymbolAndTimeframeOrderByCandleTimeDesc(String symbol, String timeframe);

    Optional<Candle> findBySymbolTokenAndTimeframeAndCandleTime(String symbolToken, String timeframe,
                                                                  LocalDateTime candleTime);

        @Query("SELECT c.symbolToken AS symbolToken, COUNT(c) AS candleCount, MAX(c.candleTime) AS updatedAt "
            + "FROM Candle c WHERE c.symbolToken IN :symbolTokens AND c.timeframe = :timeframe "
            + "GROUP BY c.symbolToken")
        List<BackfillStatus> findBackfillStatus(@Param("symbolTokens") List<String> symbolTokens,
                            @Param("timeframe") String timeframe);

    /**
     * Find distinct symbols present in candles table.
     */
    @Query("SELECT DISTINCT c.symbol FROM Candle c")
    List<String> findDistinctSymbols();
}
