package org.example.project2.domain.auth.service.local;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.auth.dto.LoginRequest;
import org.example.project2.domain.auth.dto.SignUpRequest;
import org.example.project2.domain.auth.dto.SignUpResponse;
import org.example.project2.domain.auth.exception.LoginFailedException;
import org.example.project2.domain.auth.service.token.RefreshTokenService;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.domain.auth.exception.EmailAlreadyExistsException;
import org.example.project2.domain.auth.exception.NicknameAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.example.project2.global.security.jwt.JwtProvider jwtProvider;
    private final org.example.project2.domain.auth.service.token.RefreshTokenService refreshTokenService;
    private final org.example.project2.global.security.AuthProperties authProperties;

    @Transactional
    public LoginResult login(LoginRequest request) {
        // [보안 규칙] 이메일 대소문자를 구분하지 않고 LOCAL 계정 사용자를 검색합니다.
        User user = userRepository.findByEmailIgnoreCaseAndProvider(request.email(), AuthProvider.LOCAL)
                .orElseThrow(LoginFailedException::new);

        // Argon2 패스워드 해시 매칭 검증
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new LoginFailedException();
        }

        // 유저 계정 상태 검증
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new LoginFailedException();
        }

        // 토큰 발급
        String accessToken = jwtProvider.issueToken(user.getId(), user.getRole());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);

        return new LoginResult(accessToken, refreshToken.rawToken());
    }

    public record LoginResult(String accessToken, String rawRefreshToken) {}

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new NicknameAlreadyExistsException();
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        return new SignUpResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getNickname()
        );
    }
}
