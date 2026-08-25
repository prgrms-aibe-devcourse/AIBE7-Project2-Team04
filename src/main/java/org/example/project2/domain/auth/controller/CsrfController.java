package org.example.project2.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.global.security.jwt.AuthCookieUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "인증 및 회원가입 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class CsrfController {
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final AuthCookieUtil authCookieUtil;

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "쿠키 기반 인증 API 호출 전에 XSRF-TOKEN 쿠키를 발급받습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "CSRF 토큰 쿠키 발급 성공",
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "XSRF-TOKEN 쿠키",
                            schema = @Schema(type = "string")
                    )
            )
    })
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieUtil.deleteLegacyCsrfTokenCookie().toString()
        );
        csrfTokenRepository.saveToken(csrfToken, request, response);
        return ResponseEntity.noContent().build();
    }
}
