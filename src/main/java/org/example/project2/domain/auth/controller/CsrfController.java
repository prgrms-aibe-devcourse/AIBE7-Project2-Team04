package org.example.project2.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "인증 및 회원가입 관련 API")
@RestController
@RequestMapping("/auth")
public class CsrfController {

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
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
