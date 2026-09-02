package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.dto.SignUpRequest;
import org.example.project2.domain.auth.dto.SignUpResponse;
import org.example.project2.domain.auth.service.local.AuthService;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.example.project2.domain.auth.exception.EmailAlreadyExistsException;
import org.example.project2.domain.auth.exception.NicknameAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private org.example.project2.global.security.jwt.JwtProvider jwtProvider;

    @Mock
    private org.example.project2.domain.auth.service.token.RefreshTokenService refreshTokenService;

    @Mock
    private org.example.project2.global.security.AuthProperties authProperties;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtProvider, refreshTokenService, authProperties);
    }

    @Test
    void signUpSuccess() {
        // given
        SignUpRequest request = new SignUpRequest("test@example.com", "Password123!", "nickname");
        String encodedPassword = "encodedPassword";
        UUID userId = UUID.randomUUID();

        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(userRepository.existsByNickname(request.nickname())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .id(userId)
                    .email(user.getEmail())
                    .passwordHash(user.getPasswordHash())
                    .nickname(user.getNickname())
                    .provider(user.getProvider())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .build();
        });

        // when
        SignUpResponse response = authService.signUp(request);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.nickname()).isEqualTo(request.nickname());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void signUpThrowsEmailAlreadyExistsException() {
        // given
        SignUpRequest request = new SignUpRequest("test@example.com", "Password123!", "nickname");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void signUpThrowsNicknameAlreadyExistsException() {
        // given
        SignUpRequest request = new SignUpRequest("test@example.com", "Password123!", "nickname");
        when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(userRepository.existsByNickname(request.nickname())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(NicknameAlreadyExistsException.class);
    }
}
