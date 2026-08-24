package org.example.project2.global.security.auth;

import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.entity.AuthProvider;
import org.example.project2.domain.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmailIgnoreCaseAndProvider(normalizedEmail, AuthProvider.LOCAL)
                .map(CustomUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Invalid email or password");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
