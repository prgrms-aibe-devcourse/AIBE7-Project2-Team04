package org.example.project2.domain.user.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.dto.MyProfileResponse;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.domain.user.service.UserProfileImageService;
import org.example.project2.domain.user.service.ProfileImageUrlResolver;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users/me/profile-image")
@RequiredArgsConstructor
public class UserProfileImageController {
    private final UserProfileImageService profileImageService;
    private final UserRepository userRepository;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<MyProfileResponse>> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("file") MultipartFile file
    ) {
        profileImageService.upload(userId, file);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return ResponseEntity.ok(CommonResponse.success(new MyProfileResponse(
                user.getId(), user.getEmail(), user.getNickname(), profileImageUrlResolver.resolve(user))));
    }
}
