package org.example.project2.domain.review.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 운영 감사 로그에 사용할 회전 가능한 HMAC 가명 키를 생성합니다.
 *
 * <p>운영 환경에서는 {@code REVIEW_AUDIT_PSEUDONYM_KEY}를 Secret Manager에서
 * 주입해야 합니다. 키가 없으면 개발 환경에서만 사용할 임시 키를 메모리에서
 * 생성하므로, 서버 재시작 후 가명 키가 달라집니다.</p>
 */
@Component
public class ReviewAuditPseudonymizer {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final String DEFAULT_KEY_VERSION = "dev-ephemeral";

    private final SecretKeySpec secretKey;
    private final String keyVersion;

    public ReviewAuditPseudonymizer(
            @Value("${app.review.audit.pseudonym-key:}") String configuredKey,
            @Value("${app.review.audit.pseudonym-key-version:" + DEFAULT_KEY_VERSION + "}") String configuredKeyVersion
    ) {
        byte[] keyBytes = configuredKey == null || configuredKey.isBlank()
                ? createEphemeralKey()
                : configuredKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("후기 감사 가명 키는 32바이트 이상이어야 합니다.");
        }

        String normalizedVersion = configuredKeyVersion == null || configuredKeyVersion.isBlank()
                ? DEFAULT_KEY_VERSION
                : configuredKeyVersion.trim();
        if (!normalizedVersion.matches("[A-Za-z0-9._-]{1,32}")) {
            throw new IllegalArgumentException("후기 감사 가명 키 버전 형식이 올바르지 않습니다.");
        }

        this.secretKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
        this.keyVersion = normalizedVersion;
    }

    public String userKey(UUID userId) {
        return pseudonymize("user", userId == null ? null : userId.toString());
    }

    public String matchKey(Long matchId) {
        return pseudonymize("match", matchId == null ? null : matchId.toString());
    }

    public String reviewKey(Long reviewId) {
        return pseudonymize("review", reviewId == null ? null : reviewId.toString());
    }

    public String keyVersion() {
        return keyVersion;
    }

    private String pseudonymize(String namespace, String value) {
        if (value == null) {
            return null;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal((namespace + ":" + value).getBytes(StandardCharsets.UTF_8));
            return keyVersion + ":" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("후기 감사 가명 키를 생성할 수 없습니다.", exception);
        }
    }

    private byte[] createEphemeralKey() {
        byte[] key = new byte[MINIMUM_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
