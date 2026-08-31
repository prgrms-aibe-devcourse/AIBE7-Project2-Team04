package org.example.project2.domain.matching.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.service.MatchEndService;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Match Lifecycle", description = "매칭 종료 API")
@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchEndController {
    private final MatchEndService matchEndService;

    @Operation(summary = "매칭 종료")
    @PatchMapping("/{matchId}/end")
    public ResponseEntity<CommonResponse<Void>> end(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long matchId
    ) {
        matchEndService.end(userId, matchId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "채팅방 ID를 통한 매칭 종료")
    @PatchMapping("/chatroom/{roomId}/end")
    public ResponseEntity<CommonResponse<Void>> endByRoomId(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long roomId
    ) {
        matchEndService.endByRoomId(userId, roomId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
