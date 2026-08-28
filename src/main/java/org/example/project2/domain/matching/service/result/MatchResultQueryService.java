package org.example.project2.domain.matching.service.result;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.exception.result.AuthenticatedMatchResultUserNotFoundException;
import org.example.project2.domain.matching.exception.result.MatchResultErrorCode;
import org.example.project2.domain.matching.exception.result.MatchResultException;
import org.example.project2.domain.matching.repository.MatchProposalRepository;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchResultQueryService {
    private final MatchRepository matchRepository;
    private final MatchProposalRepository matchProposalRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchResultResponseAssembler responseAssembler;

    public MatchResultResponse getLatest(UUID userId) {
        if (userId == null) {
            throw new AuthenticatedMatchResultUserNotFoundException();
        }
        Match match = matchRepository.findLatestByParticipantUserId(userId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(this::notFound);
        MatchProposal proposal = matchProposalRepository.findMatchedByRequestPair(
                        match.getRequest1().getId(),
                        match.getRequest2().getId()
                )
                .orElseThrow(this::notFound);
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(match.getId())
                .orElseThrow(this::notFound);
        return responseAssembler.assemble(proposal, match, chatRoom).responseFor(userId);
    }

    private MatchResultException notFound() {
        return new MatchResultException(MatchResultErrorCode.RESULT_NOT_FOUND);
    }
}
