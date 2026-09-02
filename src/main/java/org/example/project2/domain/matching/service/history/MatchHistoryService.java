package org.example.project2.domain.matching.service.history;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.history.MatchHistoryResponse;
import org.example.project2.domain.matching.dto.history.MatchHistoryPageResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.review.repository.UserReviewRepository;
import org.example.project2.domain.report.repository.ReportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchHistoryService {
    private static final int HISTORY_PAGE_SIZE = 10;

    private final MatchRepository matchRepository;
    private final UserReviewRepository userReviewRepository;
    private final ReportRepository reportRepository;

    public MatchHistoryPageResponse findMyHistory(UUID userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 기능입니다.");
        }

        if (page < 0 || size != HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException("매칭 이력은 10건 기준으로 조회해야 합니다.");
        }

        Pageable pageable = PageRequest.of(page, size);
        var historyPage = matchRepository.findHistoryByParticipantUserId(userId, pageable);
        List<Match> matches = historyPage.getContent();
        List<Long> matchIds = matches.stream().map(Match::getId).toList();

        Set<Long> reviewedMatchIds = matchIds.isEmpty()
                ? Collections.emptySet()
                : userReviewRepository.findReviewedMatchIdsByMatchIdInAndReviewerId(matchIds, userId);

        Set<Long> reportedMatchIds = matchIds.isEmpty()
                ? Collections.emptySet()
                : reportRepository.findReportedMatchIdsByMatchIdInAndReporterId(matchIds, userId);

        var content = matches
                .stream()
                .map(match -> toResponse(match, userId, reviewedMatchIds, reportedMatchIds))
                .toList();

        return new MatchHistoryPageResponse(
                content,
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast()
        );
    }

    private MatchHistoryResponse toResponse(
            Match match,
            UUID userId,
            Set<Long> reviewedMatchIds,
            Set<Long> reportedMatchIds
    ) {
        MatchRequest myRequest = match.getRequest1().getUser().getId().equals(userId)
                ? match.getRequest1()
                : match.getRequest2();
        MatchRequest partnerRequest = myRequest == match.getRequest1()
                ? match.getRequest2()
                : match.getRequest1();

        boolean reviewed = reviewedMatchIds.contains(match.getId());
        boolean reported = reportedMatchIds.contains(match.getId());

        return new MatchHistoryResponse(
                match.getId(),
                match.getStatus(),
                match.getMatchedAt(),
                partnerRequest.getUser().getNickname(),
                partnerRequest.getUser().getProfileImageUrl(),
                myRequest.getRegionName(),
                myRequest.getFoodCategory(),
                myRequest.getMealAt(),
                reviewed,
                reported
        );
    }
}
