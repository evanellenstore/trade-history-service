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
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CandleService {
    
    private final CandleRepository candleRepository;
    private final BrokerServiceClient brokerServiceClient;
    private final ObjectMapper objectMapper;

    public int fetchAndSave(String tradingSymbol, String symbolToken, String fromDate, String toDate,
                            String interval, String exchange) {
        String responseBody = brokerServiceClient.getCandleData(tradingSymbol, symbolToken, fromDate, toDate, interval).getBody();

        if (responseBody == null) {
            throw new IllegalStateException("Broker returned an empty candle response");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode rows = root.path("data");
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
                        .isPresent()) {
                    continue;
                }

                candles.add(Candle.builder()
                        .symbol(tradingSymbol)
                        .symbolToken(symbolToken)
                        .exchange(exchange)
                        .timeframe(interval)
                        .candleTime(candleTime)
                        .open(row.get(1).asDouble())
                        .high(row.get(2).asDouble())
                        .low(row.get(3).asDouble())
                        .close(row.get(4).asDouble())
                        .volume(row.get(5).asDouble())
                        .build());
            }

            candleRepository.saveAll(candles);
            return candles.size();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse broker candle response", exception);
        }
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
    
}