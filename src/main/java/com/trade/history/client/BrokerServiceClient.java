package com.trade.history.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "trade-broker-service", url = "${broker.service.url}")
public interface BrokerServiceClient {

    @GetMapping("/api/angelOne/candlesData")
	public ResponseEntity<String> getCandleData(@RequestParam String tradingSymbol,	@RequestParam String symbolToken,
			@RequestParam String fromDate,@RequestParam String toDate,@RequestParam String interval);
}
