package org.example.project2.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.dto.SignUpRequest;
import org.example.project2.domain.auth.dto.SignUpResponse;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeRequest;
import org.example.project2.domain.auth.dto.OAuthTokenExchangeResponse;
import org.example.project2.domain.auth.service.local.AuthService;
import org.example.project2.domain.auth.service.oauth.OAuthTokenExchangeService;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.domain.auth.exception.SignUpErrorResponse;
import org.example.project2.global.security.jwt.AuthCookieUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "인증 및 회원가입 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthTokenExchangeService oauthTokenExchangeService;
    private final AuthCookieUtil authCookieUtil;

    @Operation(summary = "이메일 회원가입", description = "이메일, 비밀번호, 닉네임을 받아 새로운 로컬 회원으로 등록합니다.")
    @Parameters({
            @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "CSRF 토큰 (GET /auth/csrf 호출 후 브라우저 쿠키에서 복사한 값을 입력)",
                    required = true,
                    schema = @Schema(type = "string")
            )
    })
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = SignUpSuccessResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (COMMON_002)",
                    content = @Content(schema = @Schema(implementation = SignUpErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이메일 혹은 닉네임 중복 (AUTH_006 / AUTH_007)",
                    content = @Content(schema = @Schema(implementation = SignUpErrorResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<CommonResponse<SignUpResponse>> signUp(
            @Valid @RequestBody SignUpRequest request
    ) {
        SignUpResponse response = authService.signUp(request);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "OAuth 일회성 코드 교환", description = "OAuth 로그인 성공 코드를 서비스 Access/Refresh Token으로 교환합니다.")
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<CommonResponse<OAuthTokenExchangeResponse>> exchangeOAuthCode(
            @Valid @RequestBody OAuthTokenExchangeRequest request
    ) {
        OAuthTokenExchangeService.ExchangeResult result = oauthTokenExchangeService.exchange(request.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        authCookieUtil.createRefreshTokenCookie(result.rawRefreshToken()).toString())
                .body(CommonResponse.success(result.response()));
    }

    // Swagger 문서화를 위한 가상 클래스 정의 (CommonResponse<SignUpResponse> 타입 스키마 표현용)
    @Schema(name = "SignUpSuccessResponse")
    private static class SignUpSuccessResponse {
        @Schema(description = "성공 여부", example = "true")
        public boolean success;

        public SignUpResponse data;

        @Schema(description = "에러 정보 (성공 시 null)", example = "null")
        public Void error;
    }
}
