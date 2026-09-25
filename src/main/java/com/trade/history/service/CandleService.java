package com.trade.history.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.history.client.BrokerServiceClient;
import com.trade.history.entity.Candle;
import org.springframework.stereotype.Service;

import com.trade.history.repository.CandleRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CandleService {
    private static final DateTimeFormatter BROKER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_CANDLES_PER_SAVE = 500;

    private final CandleRepository candleRepository;
    private final BrokerServiceClient brokerServiceClient;
    private final ObjectMapper objectMapper;

    public int fetchAndSave(String tradingSymbol, String symbolToken, String fromDate, String toDate,
                            String interval, String exchange) {
        LocalDateTime start = parseBrokerDate(fromDate);
        LocalDateTime end = parseBrokerDate(toDate);

        int CANDLE_BATCH_DAYS = 15;

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }

        LocalDateTime chunkStart = start;

        int savedCount = 0;
        while (!chunkStart.isAfter(end)) {
            LocalDateTime chunkEnd = chunkStart.plusDays(CANDLE_BATCH_DAYS - 1);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }

            String from = BROKER_DATE_FORMAT.format(chunkStart);
            String to = BROKER_DATE_FORMAT.format(chunkEnd);

            log.info("Fetching candle batch: symbolToken={}, interval={}, from={}, to={}",symbolToken, interval, from, to);

             String responseBody = brokerServiceClient.getCandleData(tradingSymbol, symbolToken,from,to,interval).getBody();

            if (responseBody == null) {
                log.warn("Broker response body is null for symbolToken={}, interval={}, from={}, to={}",symbolToken, interval, from, to);
                chunkStart = chunkEnd.plusDays(1); 
                // IMPORTANT
                continue;
            }

            int batchSavedCount = extracted(tradingSymbol, symbolToken, interval, exchange, start, end, responseBody);
            savedCount += batchSavedCount;

            // IMPORTANT: Move to the next batch
            chunkStart = chunkEnd.plusDays(1);

        }


        
        return savedCount;
    }

    /**
     * Extracts candle data from the broker response and saves it to the database.
     *
     * @param tradingSymbol The trading symbol for which to save candles.
     * @param symbolToken   The symbol token associated with the trading symbol.
     * @param interval      The interval for the candles (e.g., 1m, 5m, 1h, etc.).
     * @param exchange      The exchange where the trading symbol is listed.
     * @param start         The start date of the backfill range (inclusive).
     * @param end           The end date of the backfill range (inclusive).
     * @param responseBody  The response body containing candle data from the broker.
     * @return The number of candles saved to the database.
     */

    private int extracted(String tradingSymbol, String symbolToken, String interval, String exchange,
            LocalDateTime start, LocalDateTime end, String responseBody) {
        try {
            JsonNode rows = objectMapper.readTree(responseBody).path("data");
            if (!rows.isArray()) {
                throw new IllegalStateException("Broker response does not contain candle data");
            }

            List<Candle> candles = new ArrayList<>();
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 6) {
                    log.warn("Skipping malformed candle row: {}", row);
                    continue;
                }
                LocalDateTime candleTime = parseCandleTime(row.get(0).asText());
                if (candleRepository.findBySymbolTokenAndTimeframeAndCandleTime(symbolToken, interval, candleTime)
                        .isEmpty()) {
                    candles.add(Candle.builder().symbol(tradingSymbol).symbolToken(symbolToken)
                            .exchange(exchange).timeframe(interval).candleTime(candleTime)
                            .open(row.get(1).asDouble()).high(row.get(2).asDouble()).low(row.get(3).asDouble())
                            .close(row.get(4).asDouble()).volume(row.get(5).asDouble()).build());
                }
            }

            saveCandlesInBatches(candles);
            log.info("Saved {} candles for {} from {} to {}", candles.size(), tradingSymbol, start, end);
            return candles.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse broker candle response", exception);
        }
    }

    private void saveCandlesInBatches(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        for (int startIndex = 0; startIndex < candles.size(); startIndex += MAX_CANDLES_PER_SAVE) {
            int endIndex = Math.min(startIndex + MAX_CANDLES_PER_SAVE, candles.size());
            candleRepository.saveAllAndFlush(candles.subList(startIndex, endIndex));
        }
    }

    public Map<String, Map<String, Object>> getBackfillStatus(List<String> symbolTokens, String timeframe) {
        Map<String, Map<String, Object>> status = new LinkedHashMap<>();
        if (symbolTokens == null || symbolTokens.isEmpty() || timeframe == null || timeframe.isBlank()) {
            return status;
        }

        candleRepository.findBackfillStatus(symbolTokens, timeframe).forEach(item -> {
            Map<String, Object> itemStatus = new LinkedHashMap<>();
            itemStatus.put("status", "backfilled");
            itemStatus.put("candleCount", item.getCandleCount());
            itemStatus.put("updatedAt", item.getUpdatedAt());
            status.put(item.getSymbolToken(), itemStatus);
        });
        return status;
    }

    private LocalDateTime parseCandleTime(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception exception) {
                return LocalDateTime.parse(value);
            }
        }
    }

    private LocalDateTime parseBrokerDate(String value) {
        try {
            return LocalDateTime.parse(value, BROKER_DATE_FORMAT);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Dates must use yyyy-MM-dd HH:mm: " + value, exception);
        }
    }
    
}