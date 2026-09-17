package com.trade.history.controller;

import java.util.List;

public record CandleSyncBatchRequest(
        List<Symbol> symbols,
        String toDate,
        String interval,
        String exchange) {

    public record Symbol(String tradingSymbol, String symbolToken, String fromDate) {
    }
}