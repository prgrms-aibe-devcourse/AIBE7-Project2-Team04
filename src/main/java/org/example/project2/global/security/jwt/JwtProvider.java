package org.example.project2.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.user.entity.UserRole;
import org.example.project2.global.security.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class JwtProvider {
    private final AuthProperties p;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(p.jwt().secretKey()));
    }

    public String issueToken(UUID userId, UserRole role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime()
                + p.jwt().accessTokenExpiry().toMillis());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(p.jwt().issuer())
                .audience().add(p.jwt().audience()).and()
                .claim("roles", List.of(role.name()))
                .signWith(getSecretKey(), Jwts.SIG.HS256)
                .issuedAt(now)
                .expiration(expiration)
                .subject(userId.toString())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .requireIssuer(p.jwt().issuer())
                .requireAudience(p.jwt().audience())
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build()
                .parseSignedClaims(token) // exception
                .getPayload();
    }

}
