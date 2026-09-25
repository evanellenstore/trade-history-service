package com.trade.history.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trade.history.client.BrokerServiceClient;
import com.trade.history.repository.CandleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CandleServiceTest {

    @Test
    void fetchAndSave_shouldSaveCandlesInSmallChunks() {
        CandleRepository candleRepository = mock(CandleRepository.class);
        BrokerServiceClient brokerServiceClient = mock(BrokerServiceClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        CandleService candleService = new CandleService(candleRepository, brokerServiceClient, objectMapper);

        String responseBody = buildResponseBody(1500);
        when(brokerServiceClient.getCandleData(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(responseBody));
        when(candleRepository.findBySymbolTokenAndTimeframeAndCandleTime(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        int saved = candleService.fetchAndSave(
                "NIFTY",
                "12345",
                "2025-01-01 00:00",
                "2025-01-20 00:00",
                "1m",
                "NSE"
        );

        assertThat(saved).isEqualTo(1500);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(candleRepository, atLeastOnce()).saveAll(captor.capture());

        List<List> savedBatches = captor.getAllValues();
        assertThat(savedBatches).isNotEmpty();
        assertThat(savedBatches).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
    }

    private String buildResponseBody(int candleCount) {
        StringBuilder json = new StringBuilder("{\"data\":[");
        LocalDateTime candleTime = LocalDateTime.of(2025, 1, 1, 0, 0);

        for (int i = 0; i < candleCount; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("[\"")
                    .append(candleTime.plusMinutes(i)).append("\",")
                    .append(100.0 + i)
                    .append(",")
                    .append(110.0 + i)
                    .append(",")
                    .append(95.0 + i)
                    .append(",")
                    .append(105.0 + i)
                    .append(",")
                    .append(1000 + i)
                    .append("]");
        }

        json.append("]}");
        return json.toString();
    }
}
