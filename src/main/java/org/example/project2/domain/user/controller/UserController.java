package org.example.project2.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.dto.MyProfileResponse;
import org.example.project2.domain.user.dto.UpdateProfileRequest;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "User Profile", description = "내 프로필 정보 관련 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    @Operation(summary = "내 프로필 기본 정보 조회 (FR-01-04)")
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<MyProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UUID userId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        MyProfileResponse response = new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                profileImageUrlResolver.resolve(user)
        );

        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "내 프로필 정보 수정 (FR-01-05)")
    @PatchMapping("/me")
    @Transactional
    public ResponseEntity<CommonResponse<MyProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UUID userId,
            @Validated @RequestBody UpdateProfileRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (request.nickname() != null && !request.nickname().isBlank() && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
            }
        }

        user.updateProfile(request.nickname(), null);
        userRepository.save(user);

        MyProfileResponse response = new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                profileImageUrlResolver.resolve(user)
        );

        return ResponseEntity.ok(CommonResponse.success(response));
    }
}
