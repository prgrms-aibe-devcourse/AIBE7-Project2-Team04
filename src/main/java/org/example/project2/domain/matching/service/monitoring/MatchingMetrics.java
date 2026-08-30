package org.example.project2.domain.matching.service.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.project2.domain.matching.service.proposal.MatchProposalSelectionAttemptService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 매칭 후보 제안 탐색 시도의 운영 메트릭을 기록합니다.
 *
 * <p>사용자 ID, 요청 ID, 좌표처럼 값의 종류가 계속 늘어나는 정보는 태그로 사용하지 않고,
 * 고정된 결과 값만 태그로 사용해 Prometheus 시계열 수가 무한히 증가하지 않도록 합니다.</p>
 */
@Component
public class MatchingMetrics {
    public static final String REQUESTS_METRIC_NAME = "matching.requests";
    public static final String DURATION_METRIC_NAME = "matching.duration";
    public static final String COMPLETED_METRIC_NAME = "matching.completed";
    public static final String RESULT_TAG = "result";

    private final MeterRegistry meterRegistry;
    private final Map<MatchProposalSelectionAttemptService.AttemptResult, Counter> requestCounters;
    private final Map<MatchProposalSelectionAttemptService.AttemptResult, Timer> durationTimers;
    private final Counter completedCounter;

    public MatchingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.requestCounters = new EnumMap<>(MatchProposalSelectionAttemptService.AttemptResult.class);
        this.durationTimers = new EnumMap<>(MatchProposalSelectionAttemptService.AttemptResult.class);
        this.completedCounter = Counter.builder(COMPLETED_METRIC_NAME)
                .description("양쪽 사용자의 수락 후 커밋된 최종 매칭 수")
                .register(meterRegistry);
        registerMeters();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void record(
            MatchProposalSelectionAttemptService.AttemptResult result,
            Timer.Sample sample
    ) {
        if (result == null) {
            return;
        }
        requestCounters.get(result).increment();
        if (sample != null) {
            sample.stop(durationTimers.get(result));
        }
    }

    public void recordCompleted() {
        completedCounter.increment();
    }

    private void registerMeters() {
        for (MatchProposalSelectionAttemptService.AttemptResult result
                : MatchProposalSelectionAttemptService.AttemptResult.values()) {
            requestCounters.put(result, Counter.builder(REQUESTS_METRIC_NAME)
                    .description("매칭 후보 제안 탐색 시도 수")
                    .tag(RESULT_TAG, result.metricTag())
                    .register(meterRegistry));
            durationTimers.put(result, Timer.builder(DURATION_METRIC_NAME)
                    .description("매칭 후보 제안 탐색 처리 시간")
                    .tag(RESULT_TAG, result.metricTag())
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }
}
