package org.example.project2.global.security;

import lombok.RequiredArgsConstructor;
import org.example.project2.global.security.csrf.CsrfCookieFilter;
import org.example.project2.global.security.csrf.SpaCsrfTokenRequestHandler;
import org.example.project2.global.security.auth.CustomUserDetailsService;
import org.example.project2.global.security.handler.RestAccessDeniedHandler;
import org.example.project2.global.security.handler.RestAuthenticationEntryPoint;
import org.example.project2.global.security.jwt.JwtFilter;
import org.example.project2.global.security.oauth.CustomOAuth2UserService;
import org.example.project2.global.security.oauth.OAuth2AuthenticationFailureHandler;
import org.example.project2.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import org.example.project2.global.security.oauth.OAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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
@EnableConfigurationProperties({AuthProperties.class, OAuthProperties.class})
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
            configuration.setAllowedOrigins(List.of(p.cors().allowedOrigin(), "null"));  // null은 채팅테스트용
        }else{
            configuration.setAllowedOrigins(List.of("null"));
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
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(false) // 로컬 HTTP 테스트를 위해 false로 설정
                .sameSite("Lax") // 크로스 오리진 요청 간 전송을 위해 Lax로 완화
                .path("/")); // Refresh Token 쿠키를 인증 관련 경로에만 전송
        return repository;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler successHandler,
            OAuth2AuthenticationFailureHandler failureHandler
    ) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // OAuth2 로그인 엔드포인트는 GET 요청만 사용하며 state 값으로 요청 위조를 방지한다.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        // 인가 요청과 콜백 사이에서 OAuth2 state를 보관할 때만 세션을 생성한다.
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        return http.build();
    }

    /**
     * Prometheus는 로컬 개발 환경에서만 수집할 수 있도록 별도 체인으로 허용한다.
     *
     * <p>운영 프로필에서는 이 체인이 생성되지 않으므로, 실수로
     * {@code prometheus} 엔드포인트를 노출하더라도 기본 API 체인의 인증 정책을
     * 통과해야 한다. 운영용 메트릭 수집이 필요해지면 내부 관리망 또는 별도
     * 인증을 갖춘 관리 포트로 분리한다.</p>
     */
    @Bean
    @Order(2)
    @Profile("dev")
    public SecurityFilterChain devActuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/prometheus")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiSecurityFilterChain(
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
                                        "/auth/oauth2/exchange",
                                        "/auth/logout"
                                ).permitAll()
                                .requestMatchers(HttpMethod.GET,
                                        "/auth/csrf",
                                        "/regions",
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/swagger-ui.html",
                                        "/actuator/health",
                                        "/error"
                                ).permitAll()
                                .requestMatchers("/ws-chat/**").permitAll() // 웹소켓
                                .requestMatchers("/chat-test.html").permitAll() // 채팅테스트 페이지
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .anyRequest()
                                .authenticated()
                )
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class) // CsrfFilter로 CSRF 토큰을 생성 후 csrfCookieFilter 실행하여 쿠키로 응답에 실어줌
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // 헤더의 Access Token jwtFilter를 통해 확인, formLogin이 disable이므로 jwtFilter만 실행
        return http.build();
    }
}
