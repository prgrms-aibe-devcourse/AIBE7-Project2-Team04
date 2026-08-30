package org.example.project2.domain.matching.service.proposal;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestErrorCode;
import org.example.project2.domain.matching.exception.request.RealtimeMatchRequestException;
import org.example.project2.domain.matching.service.monitoring.MatchingMetrics;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchProposalSelectionAttemptService {

    public enum AttemptResult {
        CREATED("created"),
        NO_CANDIDATE("no_candidate"),
        SKIPPED("skipped"),
        RETRY_LATER("retry_later");

        private final String metricTag;

        AttemptResult(String metricTag) {
            this.metricTag = metricTag;
        }

        public String metricTag() {
            return metricTag;
        }
    }

    private final MatchProposalSelectionService selectionService;
    private final MatchingMetrics matchingMetrics;

    public AttemptResult attempt(UUID userId, Long requestId) {
        Timer.Sample timerSample = matchingMetrics.startTimer();
        AttemptResult result;
        try {
            result = selectionService.selectAndCreate(userId, requestId).isPresent()
                    ? AttemptResult.CREATED
                    : AttemptResult.NO_CANDIDATE;
        } catch (RealtimeMatchRequestException exception) {
            if (isTerminalState(exception.getErrorCode())) {
                log.debug(
                        "종료되거나 상태가 변경된 요청의 제안 탐색을 건너뜁니다. requestId={}, errorCode={}",
                        requestId,
                        exception.getErrorCode().getCode()
                );
                result = AttemptResult.SKIPPED;
            } else {
                logRetryableFailure(requestId, exception);
                result = AttemptResult.RETRY_LATER;
            }
        } catch (DataAccessException exception) {
            logRetryableFailure(requestId, exception);

            result = AttemptResult.RETRY_LATER;
        } catch (RuntimeException exception) {
            logRetryableFailure(requestId, exception);
            result = AttemptResult.RETRY_LATER;
        }
        matchingMetrics.record(result, timerSample);
        return result;
    }

    private boolean isTerminalState(RealtimeMatchRequestErrorCode errorCode) {
        return errorCode == RealtimeMatchRequestErrorCode.REQUEST_NOT_FOUND
                || errorCode == RealtimeMatchRequestErrorCode.REQUEST_STATE_CONFLICT;
    }

    private void logRetryableFailure(Long requestId, RuntimeException exception) {
        // 희망 설명, 임베딩, 위치와 인증 정보는 로그에 포함하지 않습니다.
        log.warn(
                "실시간 매칭 후보 제안 생성을 다음 주기에 다시 시도합니다. requestId={}, errorType={}",
                requestId,
                exception.getClass().getSimpleName()
        );
    }
}
