package org.example.project2.domain.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하로 입력해주세요.")
        String nickname,
        
        String profileImageUrl
) {}
