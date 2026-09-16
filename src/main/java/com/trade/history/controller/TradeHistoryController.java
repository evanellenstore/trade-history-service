package com.trade.history.controller;

import com.trade.history.util.ApiResponse;
import com.trade.history.service.CandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    
}

