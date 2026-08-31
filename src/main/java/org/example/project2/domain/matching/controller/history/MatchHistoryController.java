package org.example.project2.domain.matching.controller.history;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.history.MatchHistoryResponse;
import org.example.project2.domain.matching.service.history.MatchHistoryService;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Match History", description = "내 매칭 이력 조회 API")
@RestController
@RequestMapping("/matches/history")
@RequiredArgsConstructor
public class MatchHistoryController {
    private final MatchHistoryService matchHistoryService;

    @Operation(summary = "내 매칭 이력 조회")
    @GetMapping
    public ResponseEntity<CommonResponse<List<MatchHistoryResponse>>> findMyHistory(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(matchHistoryService.findMyHistory(userId)));
    }
}
