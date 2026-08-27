package org.example.project2.domain.matching.controller.proposal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.matching.dto.proposal.MatchProposalDecisionRequest;
import org.example.project2.domain.matching.dto.proposal.MatchProposalResponse;
import org.example.project2.domain.matching.exception.proposal.MatchProposalErrorResponse;
import org.example.project2.domain.matching.service.proposal.MatchProposalInteractionService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.handler.SecurityErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Realtime Match Proposals", description = "실시간 매칭 후보 프로필 조회와 수락·거절 API")
@SecurityRequirement(name = "accessTokenCookie")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/matches/realtime/proposals")
@RequiredArgsConstructor
public class MatchProposalController {
    private final MatchProposalInteractionService matchProposalInteractionService;

    @Operation(summary = "현재 매칭 후보 제안 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후보 프로필 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "현재 제안 없음",
                    content = @Content(schema = @Schema(implementation = MatchProposalErrorResponse.class)))
    })
    @GetMapping("/current")
    public ResponseEntity<CommonResponse<MatchProposalResponse>> getCurrent(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                matchProposalInteractionService.getCurrent(userId)
        ));
    }

    @Operation(summary = "매칭 후보 제안 수락 또는 거절")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf로 발급받은 XSRF-TOKEN 쿠키 값")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결정 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = SecurityErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "제안 당사자가 아님",
                    content = @Content(schema = @Schema(implementation = MatchProposalErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "만료 또는 종료된 제안",
                    content = @Content(schema = @Schema(implementation = MatchProposalErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "결정값 오류",
                    content = @Content(schema = @Schema(implementation = MatchProposalErrorResponse.class)))
    })
    @PostMapping("/{proposalId}/decision")
    public ResponseEntity<CommonResponse<MatchProposalResponse>> decide(
            @AuthenticationPrincipal UUID userId,
            @PathVariable Long proposalId,
            @Valid @RequestBody MatchProposalDecisionRequest request
    ) {
        return ResponseEntity.ok(CommonResponse.success(
                matchProposalInteractionService.decide(userId, proposalId, request)
        ));
    }
}
