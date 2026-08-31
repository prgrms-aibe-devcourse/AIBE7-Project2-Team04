package org.example.project2.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.domain.matching.repository.MatchRequestRepository;
import org.example.project2.domain.user.dto.MyProfileResponse;
import org.example.project2.domain.user.dto.UpdateProfileRequest;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.global.common.CommonResponse;
import org.example.project2.global.security.jwt.AuthCookieUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Tag(name = "User Profile", description = "내 프로필 정보 관련 API")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final AuthCookieUtil authCookieUtil;
    private final RefreshTokenService refreshTokenService;

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
                user.getProfileImageUrl(),
                user.getRole().name()
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

        user.updateProfile(request.nickname(), request.profileImageUrl());
        userRepository.save(user);

        MyProfileResponse response = new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole().name()
        );

        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "회원 탈퇴 (계정 삭제)")
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<CommonResponse<Void>> withdraw(
            @AuthenticationPrincipal UUID userId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        matchRequestRepository.deleteAllByUserIdAndStatusIn(
                userId,
                List.of(org.example.project2.domain.matching.entity.MatchRequestStatus.WAITING,
                        org.example.project2.domain.matching.entity.MatchRequestStatus.CONFIRMING)
        );

        user.withdraw();
        userRepository.save(user);

        revokeRefreshTokens(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.deleteRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieUtil.deleteLegacyRefreshTokenCookie().toString());

        return ResponseEntity.ok(CommonResponse.success(null));
    }

    private void revokeRefreshTokens(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        Arrays.stream(cookies)
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .forEach(refreshTokenService::revoke);
    }
}
