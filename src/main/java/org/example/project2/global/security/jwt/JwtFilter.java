package org.example.project2.global.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project2.domain.user.entity.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. extractToken (cookie, header)
            String token = extractToken(request);
            if (token != null) {
                // 2. extractClaims
                Claims claims = extractClaims(token);
                // 3. Authentication
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        UUID.fromString(claims.getSubject()),
                        null,
                        extractAuthorities(claims)
                );
                SecurityContext context = SecurityContextHolder.getContext();
                context.setAuthentication(auth);
            }
        } catch (Exception ignored) {
            log.warn("[JwtFilter] 인증 실패. errorCode=AUTH_001");
            SecurityContextHolder.clearContext();
        }
        // 무조건 실행이 되어야함
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // header <- 외부로 openapi 형식으로 할 때
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim(); // 'Bearer '
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        // cookie <- 내부에서 호출할 때 (HttpOnly 쿠키 방식)
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private List<GrantedAuthority> extractAuthorities(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("JWT roles claim is required");
        }

        return roles.stream()
                .map(Object::toString)
                .map(UserRole::valueOf)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private final JwtProvider jwtProvider;

    private Claims extractClaims(String token) {
        return jwtProvider.parseToken(token);
    }
}
