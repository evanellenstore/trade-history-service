package com.trade.history.dto;

public record TrainingGenerationResponse(int processed, int generated, double executionTimeSeconds) {
}
