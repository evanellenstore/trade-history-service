package com.trade.history.service;

import com.trade.history.config.TrainingDatasetProperties;
import com.trade.history.entity.BacktestMarketIndicator;
import com.trade.history.entity.Candle;
import com.trade.history.entity.TrainingDataset;
import com.trade.history.repository.BacktestMarketIndicatorRepository;
import com.trade.history.repository.CandleRepository;
import com.trade.history.repository.TrainingDatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrainingDatasetGeneratorService {
    private final CandleRepository candleRepository;
    private final BacktestMarketIndicatorRepository indicatorRepository;
    private final TrainingDatasetRepository trainingDatasetRepository;
    private final TrainingDatasetProperties properties;

    @Transactional
    public GenerationSummary generate(String symbol, String timeframe) {
        if (symbol == null || symbol.isBlank() || timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("symbol and timeframe are required");
        }
        int horizon = properties.horizonFor(timeframe);
        List<CandleRepository.SymbolTimeframe> combinations =
                candleRepository.findSymbolTimeframe(symbol, timeframe);
        if (combinations.isEmpty()) {
            throw new IllegalArgumentException("No backtest candles found for " + symbol + " " + timeframe);
        }

        GenerationSummary total = new GenerationSummary();
        for (CandleRepository.SymbolTimeframe combination : combinations) {
            total.add(generateForToken(combination, horizon));
        }
        return total;
    }

    @Transactional
    public GenerationSummary generateAll() {
        GenerationSummary total = new GenerationSummary();
        Map<String, Integer> configuredHorizons = properties.getHorizons();
        for (CandleRepository.SymbolTimeframe combination : candleRepository.findDistinctSymbolTimeframeCombinations()) {
            if (!configuredHorizons.containsKey(combination.getTimeframe())) {
                log.debug("Skipping unsupported training timeframe {}", combination.getTimeframe());
                continue;
            }
            try {
                log.info("Starting dataset generation for {} {}", combination.getSymbol(), combination.getTimeframe());
                total.add(generateForToken(combination, properties.horizonFor(combination.getTimeframe())));
            } catch (RuntimeException exception) {
                log.error("Dataset generation failed for {} {}", combination.getSymbol(), combination.getTimeframe(), exception);
            }
        }
        return total;
    }

    private GenerationSummary generateForToken(CandleRepository.SymbolTimeframe combination, int horizon) {
        Instant started = Instant.now();
        log.info("Starting dataset generation for {} {}", combination.getSymbol(), combination.getTimeframe());
        GenerationSummary summary = new GenerationSummary();
        List<PendingRow> pending = new ArrayList<>();
        IndicatorCursor indicators = new IndicatorCursor(combination.getSymbolToken(), combination.getTimeframe());
        int candleIndex = 0;
        int pageNumber = 0;
        Slice<Candle> candlePage;

        do {
            candlePage = candleRepository.findBySymbolTokenAndTimeframeOrderByCandleTimeAsc(
                    combination.getSymbolToken(), combination.getTimeframe(),
                    PageRequest.of(pageNumber++, properties.getPageSize()));
            for (Candle candle : candlePage.getContent()) {
                BacktestMarketIndicator indicator = indicators.match(candle.getCandleTime());
                if (isValid(indicator) && Double.isFinite(candle.getClose()) && candle.getClose() > 0) {
                    pending.add(new PendingRow(candle, indicator, candleIndex + horizon));
                }

                while (!pending.isEmpty() && pending.get(0).futureCandleIndex() == candleIndex) {
                    PendingRow row = pending.remove(0);
                    double futureReturnPct = ((candle.getClose() - row.candle().getClose())
                            / row.candle().getClose()) * 100.0;
                    if (Double.isFinite(futureReturnPct)) {
                        summary.addCandidate(toDataset(row, candle, futureReturnPct));
                    }
                }
                candleIndex++;
                if (summary.pendingRecords().size() >= properties.getInsertBatchSize()) {
                    persist(summary.pendingRecords(), combination, summary);
                }
            }
        } while (candlePage.hasNext());

        if (!summary.pendingRecords().isEmpty()) {
            persist(summary.pendingRecords(), combination, summary);
        }
        summary.setElapsedSeconds(Duration.between(started, Instant.now()).toMillis() / 1000.0);
        log.info("Completed dataset generation for {} {}. Processed: {} rows, Generated: {} records, Execution Time: {}s",
                combination.getSymbol(), combination.getTimeframe(), summary.processed(), summary.generated(), summary.elapsedSeconds());
        return summary;
    }

    private void persist(List<TrainingDataset> batch, CandleRepository.SymbolTimeframe combination,
                         GenerationSummary summary) {
        Set<LocalDateTime> existing = new HashSet<>(trainingDatasetRepository.findExistingCandleTimes(
                combination.getSymbolToken(), combination.getTimeframe(),
                batch.stream().map(TrainingDataset::getCandleTime).toList()));
        List<TrainingDataset> newRows = batch.stream()
                .filter(row -> !existing.contains(row.getCandleTime()))
                .toList();
        if (!newRows.isEmpty()) {
            trainingDatasetRepository.saveAll(newRows);
            trainingDatasetRepository.flush();
            summary.generated += newRows.size();
        }
        summary.processed += batch.size();
        batch.clear();
        log.info("Processed: {} rows; Generated: {} records", summary.processed(), summary.generated());
    }

    private TrainingDataset toDataset(PendingRow row, Candle futureCandle, double futureReturnPct) {
        BacktestMarketIndicator i = row.indicator();
        return TrainingDataset.builder()
                .symbol(row.candle().getSymbol()).symbolToken(row.candle().getSymbolToken())
                .timeframe(row.candle().getTimeframe()).candleTime(row.candle().getCandleTime())
                .rsi14(i.getMomentum_rsi14()).adx(i.getTrend_adx()).plusDi(i.getTrend_plusDi()).minusDi(i.getTrend_minusDi())
                .macd(i.getMomentum_macd()).macdSignal(i.getMomentum_macdSignal()).macdHistogram(i.getMomentum_macdHistogram())
                .ema20(i.getTrend_ema20()).ema50(i.getTrend_ema50()).ema100(i.getTrend_ema100()).ema200(i.getTrend_ema200())
                .atr(i.getVolatility_atr()).vwap(i.getVolume_vwap()).stochasticK(i.getMomentum_stochasticK())
                .stochasticD(i.getMomentum_stochasticD()).cci(i.getMomentum_cci()).mfi(i.getVolume_mfi()).cmf(i.getVolume_cmf())
                .bbWidth(i.getVolatility_bbWidth()).closePrice(row.candle().getClose()).futureClosePrice(futureCandle.getClose())
                .futureReturnPct(futureReturnPct)
                .target(futureReturnPct >= properties.getTargetReturnThresholdPct() ? 1 : 0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private boolean isValid(BacktestMarketIndicator indicator) {
        return indicator != null && indicator.getCandleTime() != null
                && required(indicator.getMomentum_rsi14()) && required(indicator.getTrend_adx())
                && required(indicator.getMomentum_macd());
    }

    private boolean required(Double value) {
        return value != null && Double.isFinite(value);
    }

    private record PendingRow(Candle candle, BacktestMarketIndicator indicator, int futureCandleIndex) { }

    private final class IndicatorCursor {
        private final String symbolToken;
        private final String timeframe;
        private int pageNumber;
        private List<BacktestMarketIndicator> page = List.of();
        private int index;
        private boolean hasNext = true;

        private IndicatorCursor(String symbolToken, String timeframe) {
            this.symbolToken = symbolToken;
            this.timeframe = timeframe;
        }

        private BacktestMarketIndicator match(LocalDateTime candleTime) {
            while (hasNext) {
                if (index >= page.size()) {
                    Slice<BacktestMarketIndicator> next = indicatorRepository
                            .findBySymbolTokenAndTimeframeOrderByCandleTimeAsc(symbolToken, timeframe,
                                    PageRequest.of(pageNumber++, properties.getPageSize()));
                    page = next.getContent();
                    index = 0;
                    hasNext = next.hasNext() || !page.isEmpty();
                    if (page.isEmpty()) {
                        hasNext = false;
                        return null;
                    }
                }
                BacktestMarketIndicator current = page.get(index);
                if (current.getCandleTime().isBefore(candleTime)) {
                    index++;
                    continue;
                }
                if (current.getCandleTime().equals(candleTime)) {
                    index++;
                    return current;
                }
                return null;
            }
            return null;
        }
    }

    public static class GenerationSummary {
        private int processed;
        private int generated;
        private double elapsedSeconds;
        private final List<TrainingDataset> pendingRecords = new ArrayList<>();

        private void add(GenerationSummary other) {
            processed += other.processed;
            generated += other.generated;
            elapsedSeconds += other.elapsedSeconds;
        }
        private void addCandidate(TrainingDataset row) { pendingRecords.add(row); }
        private List<TrainingDataset> pendingRecords() { return pendingRecords; }
        public int processed() { return processed; }
        public int generated() { return generated; }
        public double elapsedSeconds() { return elapsedSeconds; }
        private void setElapsedSeconds(double value) { elapsedSeconds = value; }
    }
}
