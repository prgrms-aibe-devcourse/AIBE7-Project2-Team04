package org.example.project2.domain.matching.service;

import java.time.Instant;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.chat.repository.ChatRoomRepository;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchEndService {
    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public void end(UUID userId, Long matchId) {
        Match match = matchRepository.findByIdAndParticipantUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 매칭을 찾을 수 없거나 종료 권한이 없습니다."));

        match.complete(Instant.now());
        ChatRoom chatRoom = chatRoomRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("매칭 채팅방을 찾을 수 없습니다."));
        chatRoom.close(Instant.now());
    }

    @Transactional
    public void endByRoomId(UUID userId, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        
        Match match = chatRoom.getMatch();
        if (match == null) {
            throw new IllegalArgumentException("채팅방에 연결된 매칭 정보를 찾을 수 없습니다.");
        }
        
        Match validMatch = matchRepository.findByIdAndParticipantUserId(match.getId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 매칭에 종료 권한이 없습니다."));

        validMatch.complete(Instant.now());
        chatRoom.close(Instant.now());
    }
}
