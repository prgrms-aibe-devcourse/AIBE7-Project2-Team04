package org.example.project2.domain.review.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewAuditPseudonymizerTest {
    private static final String KEY = "a".repeat(32);
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void createsStableNamespaceSeparatedPseudonymousKeys() {
        ReviewAuditPseudonymizer pseudonymizer = new ReviewAuditPseudonymizer(KEY, "v1");

        String userKey = pseudonymizer.userKey(USER_ID);

        assertThat(userKey).startsWith("v1:");
        assertThat(userKey).isEqualTo(pseudonymizer.userKey(USER_ID));
        assertThat(userKey).isNotEqualTo(pseudonymizer.matchKey(1L));
        assertThat(userKey).doesNotContain(USER_ID.toString());
        assertThat(pseudonymizer.userKey(null)).isNull();
        assertThat(pseudonymizer.keyVersion()).isEqualTo("v1");
    }

    @Test
    void rejectsShortConfiguredKey() {
        assertThatThrownBy(() -> new ReviewAuditPseudonymizer("too-short", "v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32바이트");
    }
}
