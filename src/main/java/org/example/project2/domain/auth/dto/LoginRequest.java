package org.example.project2.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 일반(로컬) 이메일 로그인을 위한 요청 DTO입니다.
 */
public record LoginRequest(
        @NotBlank(message = "이메일은 필수 입력 사항입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수 입력 사항입니다.")
        String password
) {}
