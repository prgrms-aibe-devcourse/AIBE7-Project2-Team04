package org.example.project2.domain.auth.service;

import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService;
import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService.KakaoLoginUser;
import org.example.project2.domain.auth.service.oauth.KakaoOAuthUserService.KakaoUserInfo;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoOAuthUserServiceTest {
    @Mock
    private UserRepository userRepository;

    private KakaoOAuthUserService service;

    @BeforeEach
    void setUp() {
        service = new KakaoOAuthUserService(userRepository);
    }

    @Test
    void createsNewKakaoUser() {
        KakaoUserInfo userInfo = userInfo(" User@Test.com ", true, "kakao-user");
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KakaoLoginUser result = service.findOrCreate(userInfo);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@test.com");
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(captor.getValue().getProviderId()).isEqualTo("12345");
        assertThat(captor.getValue().getPasswordHash()).isNull();
        assertThat(result.profileSetupRequired()).isFalse();
    }

    @Test
    void returnsExistingKakaoUser() {
        User existing = kakaoUser("existing-user");
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(existing));

        KakaoLoginUser result = service.findOrCreate(userInfo("user@test.com", true, "new-name"));

        assertThat(result.user()).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertThatThrownBy(() -> service.findOrCreate(userInfo("user@test.com", false, "nickname")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("AUTH_004"));
    }

    @Test
    void rejectsEmailAlreadyUsedByAnotherLoginMethod() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.findOrCreate(userInfo("user@test.com", true, "nickname")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo("AUTH_005"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsTemporaryNicknameWhenKakaoNicknameIsAlreadyUsed() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByNickname("duplicate-name")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KakaoLoginUser result = service.findOrCreate(
                userInfo("user@test.com", true, "duplicate-name")
        );

        assertThat(result.user().getNickname()).matches("사용자_[A-Za-z0-9]{8}");
        assertThat(result.profileSetupRequired()).isTrue();
    }

    private KakaoUserInfo userInfo(String email, boolean verified, String nickname) {
        return new KakaoUserInfo(
                "12345",
                email,
                verified,
                nickname,
                "https://example.com/profile.png"
        );
    }

    private User kakaoUser(String nickname) {
        return User.builder()
                .email("user@test.com")
                .provider(AuthProvider.KAKAO)
                .providerId("12345")
                .nickname(nickname)
                .build();
    }
}
