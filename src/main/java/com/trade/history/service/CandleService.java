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
    private static final int MAX_BROKER_WINDOW_DAYS = 29;
    
    private final CandleRepository candleRepository;
    private final BrokerServiceClient brokerServiceClient;
    private final ObjectMapper objectMapper;

    public int fetchAndSave(String tradingSymbol, String symbolToken, String fromDate, String toDate,
                            String interval, String exchange) {
        LocalDateTime start = parseBrokerDate(fromDate);
        LocalDateTime end = parseBrokerDate(toDate);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }

        int saved = 0;
        LocalDateTime windowStart = start;
        while (!windowStart.isAfter(end)) {
            LocalDateTime windowEnd = windowStart.plusDays(MAX_BROKER_WINDOW_DAYS);
            if (windowEnd.isAfter(end)) {
                windowEnd = end;
            }
            saved += fetchAndSaveWindow(tradingSymbol, symbolToken, windowStart, windowEnd, interval, exchange);
            windowStart = windowEnd.plusMinutes(1);
        }
        return saved;
    }

    private int fetchAndSaveWindow(String tradingSymbol, String symbolToken, LocalDateTime fromDate,
                                   LocalDateTime toDate, String interval, String exchange) {
        String responseBody = brokerServiceClient.getCandleData(tradingSymbol, symbolToken,
                BROKER_DATE_FORMAT.format(fromDate), BROKER_DATE_FORMAT.format(toDate), interval).getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Broker returned an empty candle response");
        }

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
            candleRepository.saveAll(candles);
            log.info("Saved {} candles for {} from {} to {}", candles.size(), tradingSymbol, fromDate, toDate);
            return candles.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse broker candle response", exception);
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