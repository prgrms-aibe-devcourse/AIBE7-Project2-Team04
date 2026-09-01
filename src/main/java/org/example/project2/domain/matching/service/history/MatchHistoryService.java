package org.example.project2.domain.matching.service.history;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.history.MatchHistoryResponse;
import org.example.project2.domain.matching.dto.history.MatchHistoryPageResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.review.repository.UserReviewRepository;
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

    public MatchHistoryPageResponse findMyHistory(UUID userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 기능입니다.");
        }

        if (page < 0 || size != HISTORY_PAGE_SIZE) {
            throw new IllegalArgumentException("留ㅼ묶 ?대젰??10嫄?湲곗?濡쒖쫰?섏빞 ?⑸땲??");
        }

        Pageable pageable = PageRequest.of(page, size);
        var historyPage = matchRepository.findHistoryByParticipantUserId(userId, pageable);
        var content = historyPage
                .stream()
                .map(match -> toResponse(match, userId))
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

    private MatchHistoryResponse toResponse(Match match, UUID userId) {
        MatchRequest myRequest = match.getRequest1().getUser().getId().equals(userId)
                ? match.getRequest1()
                : match.getRequest2();
        MatchRequest partnerRequest = myRequest == match.getRequest1()
                ? match.getRequest2()
                : match.getRequest1();

        boolean reviewed = userReviewRepository.existsByMatch_IdAndReviewer_IdAndReviewee_Id(
                match.getId(),
                userId,
                partnerRequest.getUser().getId()
        );

        return new MatchHistoryResponse(
                match.getId(),
                match.getStatus(),
                match.getMatchedAt(),
                partnerRequest.getUser().getNickname(),
                partnerRequest.getUser().getProfileImageUrl(),
                myRequest.getRegionName(),
                myRequest.getFoodCategory(),
                myRequest.getMealAt(),
                reviewed
        );
    }
}
