package com.trade.history.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "training.dataset")
public class TrainingDatasetProperties {
    private int pageSize = 1000;
    private int insertBatchSize = 500;
    private double targetReturnThresholdPct = 3.0;
    private Map<String, Integer> horizons = new LinkedHashMap<>(Map.of(
            "ONE_MINUTE", 50,
            "FIVE_MINUTE", 30,
            "FIFTEEN_MINUTE", 20,
            "ONE_HOUR", 10
    ));

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getInsertBatchSize() { return insertBatchSize; }
    public void setInsertBatchSize(int insertBatchSize) { this.insertBatchSize = insertBatchSize; }
    public double getTargetReturnThresholdPct() { return targetReturnThresholdPct; }
    public void setTargetReturnThresholdPct(double targetReturnThresholdPct) { this.targetReturnThresholdPct = targetReturnThresholdPct; }
    public Map<String, Integer> getHorizons() { return horizons; }
    public void setHorizons(Map<String, Integer> horizons) { this.horizons = horizons; }

    public int horizonFor(String timeframe) {
        Integer horizon = horizons.get(timeframe);
        if (horizon == null || horizon < 1) {
            throw new IllegalArgumentException("No positive training horizon configured for timeframe: " + timeframe);
        }
        return horizon;
    }
}
