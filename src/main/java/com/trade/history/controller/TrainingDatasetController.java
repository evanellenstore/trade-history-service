package com.trade.history.controller;

import com.trade.history.dto.TrainingGenerationResponse;
import com.trade.history.service.TrainingDatasetGeneratorService;
import com.trade.history.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/training")
@RequiredArgsConstructor
@Slf4j
public class TrainingDatasetController {
    private final TrainingDatasetGeneratorService generatorService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<TrainingGenerationResponse>> generate(
            @RequestParam String symbol, @RequestParam String timeframe) {
        log.info("Received training dataset generation request for {} {}", symbol, timeframe);
        return ResponseEntity.ok(ApiResponse.success(toResponse(generatorService.generate(symbol, timeframe)),
                "Training dataset generation completed"));
    }

    @PostMapping("/generate/all")
    public ResponseEntity<ApiResponse<TrainingGenerationResponse>> generateAll() {
        log.info("Received training dataset generation request for all configured symbols and timeframes");
        return ResponseEntity.ok(ApiResponse.success(toResponse(generatorService.generateAll()),
                "Training dataset generation completed for all configured combinations"));
    }

    private TrainingGenerationResponse toResponse(TrainingDatasetGeneratorService.GenerationSummary summary) {
        return new TrainingGenerationResponse(summary.processed(), summary.generated(), summary.elapsedSeconds());
    }
}
