package org.example.project2.domain.matching.service.result;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.matching.dto.proposal.MatchProposalPartnerProfileResponse;
import org.example.project2.domain.matching.dto.result.MatchResultCompatibilityResponse;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.personality.entity.PersonalityTag;
import org.example.project2.domain.personality.entity.UserPersonalityProfile;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MatchResultResponseAssembler {
    private final UserPersonalityProfileRepository personalityProfileRepository;

    public MatchResultViews assemble(MatchProposal proposal, Match match, ChatRoom chatRoom) {
        MatchRequest request1 = proposal.getRequest1();
        MatchRequest request2 = proposal.getRequest2();
        Map<UUID, UserPersonalityProfile> profilesByUserId = loadProfiles(request1, request2);
        return new MatchResultViews(
                request1.getUser().getId(),
                toResponse(proposal, request1, match, chatRoom, profilesByUserId),
                request2.getUser().getId(),
                toResponse(proposal, request2, match, chatRoom, profilesByUserId)
        );
    }

    private MatchResultResponse toResponse(
            MatchProposal proposal,
            MatchRequest viewerRequest,
            Match match,
            ChatRoom chatRoom,
            Map<UUID, UserPersonalityProfile> profilesByUserId
    ) {
        MatchRequest partnerRequest = proposal.getOtherRequest(viewerRequest.getId());
        if (partnerRequest == null || partnerRequest.getUser() == null) {
            throw new IllegalStateException("매칭 결과의 상대 사용자 정보를 확인할 수 없습니다.");
        }
        UserPersonalityProfile partnerProfile = profilesByUserId.get(partnerRequest.getUser().getId());
        Set<PersonalityTag> styleTags = partnerProfile == null ? Set.of() : partnerProfile.getStyleTags();
        var partner = partnerRequest.getUser();
        return new MatchResultResponse(
                match.getId(),
                match.getStatus(),
                chatRoom.getId(),
                compatibilityFor(proposal, viewerRequest),
                new MatchProposalPartnerProfileResponse(
                        partner.getId(),
                        partner.getNickname(),
                        partner.getProfileImageUrl(),
                        partner.getDescription(),
                        styleTags
                )
        );
    }

    private MatchResultCompatibilityResponse compatibilityFor(
            MatchProposal proposal,
            MatchRequest viewerRequest
    ) {
        var snapshot = proposal.getScoreSnapshot();
        if (snapshot == null) {
            return null;
        }
        boolean viewerIsRequest1 = viewerRequest.getId().equals(proposal.getRequest1().getId());
        Short myScore = viewerIsRequest1 ? snapshot.sourceToTargetScore() : snapshot.targetToSourceScore();
        return new MatchResultCompatibilityResponse(
                myScore,
                viewerIsRequest1
                        ? snapshot.sourceToTargetMatchedTags()
                        : snapshot.targetToSourceMatchedTags(),
                viewerIsRequest1
                        ? snapshot.sourceToTargetReasons()
                        : snapshot.targetToSourceReasons(),
                snapshot.formulaVersion()
        );
    }

    private Map<UUID, UserPersonalityProfile> loadProfiles(
            MatchRequest request1,
            MatchRequest request2
    ) {
        List<UUID> userIds = List.of(request1.getUser().getId(), request2.getUser().getId());
        List<UserPersonalityProfile> profiles = personalityProfileRepository.findAllByUserIdIn(userIds);
        if (profiles == null || profiles.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserPersonalityProfile> result = new LinkedHashMap<>();
        profiles.forEach(profile -> {
            if (profile != null && profile.getUserId() != null) {
                result.putIfAbsent(profile.getUserId(), profile);
            }
        });
        return Map.copyOf(result);
    }

    public record MatchResultViews(
            UUID request1UserId,
            MatchResultResponse request1Response,
            UUID request2UserId,
            MatchResultResponse request2Response
    ) {
        public MatchResultResponse responseFor(UUID userId) {
            if (request1UserId.equals(userId)) {
                return request1Response;
            }
            if (request2UserId.equals(userId)) {
                return request2Response;
            }
            throw new IllegalArgumentException("매칭 참여자만 결과를 조회할 수 있습니다.");
        }
    }
}
