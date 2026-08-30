package org.example.project2.domain.matching.service.monitoring;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.project2.domain.matching.service.proposal.MatchProposalSelectionAttemptService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingMetricsTest {
    @Test
    void recordsRequestCountAndDurationForEachResult() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MatchingMetrics metrics = new MatchingMetrics(registry);

        Timer.Sample sample = metrics.startTimer();
        metrics.record(MatchProposalSelectionAttemptService.AttemptResult.CREATED, sample);

        assertThat(registry.get(MatchingMetrics.REQUESTS_METRIC_NAME)
                .tag(MatchingMetrics.RESULT_TAG, "created")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get(MatchingMetrics.DURATION_METRIC_NAME)
                .tag(MatchingMetrics.RESULT_TAG, "created")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(registry.get(MatchingMetrics.DURATION_METRIC_NAME)
                .tag(MatchingMetrics.RESULT_TAG, "created")
                .timer()
                .totalTime(TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void registersOnlyFiniteResultTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new MatchingMetrics(registry);

        assertThat(registry.find(MatchingMetrics.REQUESTS_METRIC_NAME).counters())
                .hasSize(MatchProposalSelectionAttemptService.AttemptResult.values().length);
        assertThat(registry.find(MatchingMetrics.DURATION_METRIC_NAME).timers())
                .hasSize(MatchProposalSelectionAttemptService.AttemptResult.values().length);
    }

    @Test
    void recordsCompletedMatchWithoutResultTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MatchingMetrics metrics = new MatchingMetrics(registry);

        metrics.recordCompleted();
        metrics.recordCompleted();

        assertThat(registry.get(MatchingMetrics.COMPLETED_METRIC_NAME)
                .counter()
                .count()).isEqualTo(2.0);
    }
}
