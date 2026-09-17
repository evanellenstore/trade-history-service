package com.trade.history.controller;

import java.util.List;

public record CandleBackfillBatchRequest(
        List<Symbol> symbols,
        String fromDate,
        String toDate,
        String interval,
        String exchange) {

    public record Symbol(String tradingSymbol, String symbolToken) {
    }
}
