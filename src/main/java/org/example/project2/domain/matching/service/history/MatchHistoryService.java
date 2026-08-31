package org.example.project2.domain.matching.service.history;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.history.MatchHistoryResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchHistoryService {
    private static final int HISTORY_LIMIT = 30;

    private final MatchRepository matchRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public List<MatchHistoryResponse> findMyHistory(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요한 기능입니다.");
        }

        return matchRepository.findHistoryByParticipantUserId(userId, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(match -> toResponse(match, userId))
                .toList();
    }

    private MatchHistoryResponse toResponse(Match match, UUID userId) {
        MatchRequest myRequest = match.getRequest1().getUser().getId().equals(userId)
                ? match.getRequest1()
                : match.getRequest2();
        MatchRequest partnerRequest = myRequest == match.getRequest1()
                ? match.getRequest2()
                : match.getRequest1();

        return new MatchHistoryResponse(
                match.getId(),
                match.getStatus(),
                match.getMatchedAt(),
                partnerRequest.getUser().getNickname(),
                profileImageUrlResolver.resolve(partnerRequest.getUser()),
                myRequest.getRegionName(),
                myRequest.getFoodCategory(),
                myRequest.getMealAt()
        );
    }
}
