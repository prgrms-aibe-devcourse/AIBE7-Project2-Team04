package org.example.project2.domain.auth.dto;

/**
 * 일반(로컬) 이메일 로그인 성공 시 반환하는 응답 DTO입니다.
 * 실제 토큰 문자열은 HttpOnly 쿠키로 전송되므로, 응답 바디에는 메타데이터만 포함합니다.
 */
public record LoginResponse(
        String tokenType,
        long expiresIn
) {}
