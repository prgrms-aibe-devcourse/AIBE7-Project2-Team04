package org.example.project2.domain.matching.controller.result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.result.MatchResultResponse;
import org.example.project2.domain.matching.exception.result.MatchResultErrorResponse;
import org.example.project2.domain.matching.service.result.MatchResultQueryService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Realtime Match Results", description = "WebSocket 연결 유실 시 최신 매칭 결과 복구 조회 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/matches/realtime/results")
@RequiredArgsConstructor
public class MatchResultController {
    private final MatchResultQueryService matchResultQueryService;

    @Operation(summary = "내 최신 매칭 결과 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최신 매칭 결과 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "매칭 결과 없음",
                    content = @Content(schema = @Schema(implementation = MatchResultErrorResponse.class)))
    })
    @GetMapping("/latest")
    public ResponseEntity<CommonResponse<MatchResultResponse>> getLatest(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(matchResultQueryService.getLatest(userId)));
    }
}
