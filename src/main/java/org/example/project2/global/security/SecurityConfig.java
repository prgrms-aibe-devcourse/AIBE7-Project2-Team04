package org.example.project2.global.security;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.security.csrf.CsrfCookieFilter;
import org.example.project2.global.security.csrf.SpaCsrfTokenRequestHandler;
import org.example.project2.global.security.auth.CustomUserDetailsService;
import org.example.project2.global.security.handler.RestAccessDeniedHandler;
import org.example.project2.global.security.handler.RestAuthenticationEntryPoint;
import org.example.project2.global.security.jwt.JwtFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    private final AuthProperties p;
    private final JwtFilter jwtFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final SpaCsrfTokenRequestHandler csrfTokenRequestHandler;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // PasswordEncoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encodingMap = Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
        );
        return new DelegatingPasswordEncoder(p.password().encodingId(), encodingMap);
    }

    /*
    DaoAuthenticationProvider: DB의 사용자 정보(CustomDetailsService)와 PasswordEncoder를 연결
    로그인 시 입력받은 이메일/비밀번호가 일치하는지 검증하는 역할 수행
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (StringUtils.hasText(p.cors().allowedOrigin())) {
            configuration.setAllowedOrigins(List.of(p.cors().allowedOrigin()));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        // CSRF 토큰 쿠키는 프론트엔드 자바스크립트가 읽어서 헤더에 담아야 하므로 HttpOnly = false로 설정
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(true) // HTTPS 암호화 통신에서만 전송
                .sameSite("Strict") // 다른 사이트에서 시작된 요청에 쿠키가 전송되는 것을 제한
                .path("/")); // Refresh Token 쿠키를 인증 관련 경로에만 전송
        return repository;
    }

    // SecurityFilterChain
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            CookieCsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        http
                // cors 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        // 서버가 CSRF 토큰을 생성할 때 어떤 구성 및 속성으로 생성할 지 csrfTokenRepository가 설계도 역할
                        .csrfTokenRepository(csrfTokenRepository)
                        // 요청 헤더의 csrf 토큰을 읽고 검증하는 해석기로 csrfTokenRequestHandler 사용
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                // JWT 방식에서 필요없는 기본 속성 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // JWT는 서버 세션을 생성하거나 유지하지 않음 -> 무상태성(STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/").permitAll()
                                .requestMatchers(HttpMethod.POST,
                                        "/auth/signup",
                                        "/auth/login",
                                        "/auth/token/refresh",
                                        "/auth/oauth2/exchange"
                                ).permitAll()
                                .requestMatchers(HttpMethod.GET,
                                        "/auth/csrf",
                                        "/oauth2/authorization/**",
                                        "/login/oauth2/code/**",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/actuator/health"
                                ).permitAll()
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .anyRequest()
                                .authenticated()
                )
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class) // CsrfFilter로 CSRF 토큰을 생성 후 csrfCookieFilter 실행하여 쿠키로 응답에 실어줌
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // 헤더의 Access Token jwtFilter를 통해 확인, formLogin이 disable이므로 jwtFilter만 실행
        return http.build();
    }
}
