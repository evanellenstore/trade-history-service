package com.trade.history.controller;

import com.trade.history.util.ApiResponse;
import com.trade.history.service.CandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/history")
@Slf4j
@RequiredArgsConstructor
public class TradeHistoryController {

    private final CandleService candleService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Object>> health() {
        return ResponseEntity.ok(ApiResponse.success("UP", "Service is running"));
    }

    @GetMapping("/candles/status")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Object>>>> candleBackfillStatus(
            @RequestParam List<String> symbolTokens,
            @RequestParam String timeframe) {
        return ResponseEntity.ok(ApiResponse.success(
                candleService.getBackfillStatus(symbolTokens, timeframe),
                "Candle backfill status loaded"));
    }

    @GetMapping("/candles/backfill")
    public ResponseEntity<ApiResponse<Integer>> backfillCandles(
            @RequestParam String tradingSymbol,
            @RequestParam String symbolToken,
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam String interval,
            @RequestParam(defaultValue = "NSE") String exchange) {
        int savedCount = candleService.fetchAndSave(
                tradingSymbol, symbolToken, fromDate, toDate, interval, exchange);
        return ResponseEntity.ok(ApiResponse.success(savedCount, "Candles saved successfully"));
    }

    @PostMapping("/candles/backfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillCandlesBatch(
            @RequestBody CandleBackfillBatchRequest request) {
        if (request.symbols() == null || request.symbols().isEmpty()
                || request.fromDate() == null || request.toDate() == null || request.interval() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.success(
                    Map.of("message", "At least one symbol, both dates, and an interval are required"),
                    "Invalid backfill request"));
        }

        List<String> successfulSymbols = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();
        int savedCandles = 0;
        String exchange = request.exchange() == null || request.exchange().isBlank() ? "NSE" : request.exchange();

        for (CandleBackfillBatchRequest.Symbol symbol : request.symbols()) {
            try {
                savedCandles += candleService.fetchAndSave(
                        symbol.tradingSymbol(), symbol.symbolToken(), request.fromDate(), request.toDate(),
                        request.interval(), exchange);
                successfulSymbols.add(symbol.tradingSymbol());
            } catch (Exception exception) {
                log.error("Candle backfill failed for {}", symbol.tradingSymbol(), exception);
                failedSymbols.add(symbol.tradingSymbol());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedSymbols", request.symbols().size());
        result.put("savedCandles", savedCandles);
        result.put("successfulSymbols", successfulSymbols);
        result.put("failedSymbols", failedSymbols);
        return ResponseEntity.ok(ApiResponse.success(result, "Candle backfill completed"));
    }

    @PostMapping("/candles/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncCandlesBatch(
            @RequestBody CandleSyncBatchRequest request) {
        if (request.symbols() == null || request.symbols().isEmpty()
                || request.toDate() == null || request.interval() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.success(
                    Map.of("message", "At least one symbol, a target date, and an interval are required"),
                    "Invalid candle sync request"));
        }

        List<String> successfulSymbols = new ArrayList<>();
        List<String> failedSymbols = new ArrayList<>();
        int savedCandles = 0;
        String exchange = request.exchange() == null || request.exchange().isBlank() ? "NSE" : request.exchange();

        for (CandleSyncBatchRequest.Symbol symbol : request.symbols()) {
            try {
                savedCandles += candleService.fetchAndSave(
                        symbol.tradingSymbol(), symbol.symbolToken(), symbol.fromDate(), request.toDate(),
                        request.interval(), exchange);
                successfulSymbols.add(symbol.tradingSymbol());
            } catch (Exception exception) {
                log.error("Candle sync failed for {}", symbol.tradingSymbol(), exception);
                failedSymbols.add(symbol.tradingSymbol());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedSymbols", request.symbols().size());
        result.put("savedCandles", savedCandles);
        result.put("successfulSymbols", successfulSymbols);
        result.put("failedSymbols", failedSymbols);
        return ResponseEntity.ok(ApiResponse.success(result, "Candle sync completed"));
    }

    
}

