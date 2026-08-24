package org.example.project2.global.security.auth;

import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.entity.User;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.domain.user.entity.UserStatus;
import org.example.project2.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService userDetailsService = new CustomUserDetailsService(userRepository);

    @Test
    void loadsOnlyLocalAccountUsingNormalizedEmail() {
        User user = localUser(UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCaseAndProvider("user@test.com", AuthProvider.LOCAL))
                .thenReturn(Optional.of(user));

        CustomUserPrincipal principal = (CustomUserPrincipal) userDetailsService
                .loadUserByUsername("  USER@Test.com ");

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.getUsername()).isEqualTo(user.getEmail());
        assertThat(principal.getPassword()).isEqualTo(user.getPasswordHash());
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        verify(userRepository).findByEmailIgnoreCaseAndProvider("user@test.com", AuthProvider.LOCAL);
    }

    @Test
    void missingLocalAccountIsReportedAsInvalidCredentials() {
        when(userRepository.findByEmailIgnoreCaseAndProvider("user@test.com", AuthProvider.LOCAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void blankEmailDoesNotQueryRepository() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(" "))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void withdrawnUserPrincipalIsDisabled() {
        assertThat(CustomUserPrincipal.from(localUser(UserStatus.WITHDRAWN)).isEnabled()).isFalse();
    }

    private User localUser(UserStatus status) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .passwordHash("{argon2}encoded")
                .provider(AuthProvider.LOCAL)
                .nickname("user")
                .role(UserRole.USER)
                .status(status)
                .build();
    }
}
