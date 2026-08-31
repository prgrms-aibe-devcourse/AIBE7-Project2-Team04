package org.example.project2.domain.review.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSummaryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesScoreOnlyWhenEnoughReviewsAreAvailable() throws Exception {
        MyReviewSummaryResponse available = new MyReviewSummaryResponse(
                ReviewScoreStatus.AVAILABLE,
                new BigDecimal("84.0"),
                8
        );
        MyReviewSummaryResponse insufficient = new MyReviewSummaryResponse(
                ReviewScoreStatus.INSUFFICIENT_REVIEWS,
                null,
                2
        );
        MyReviewSummaryResponse empty = new MyReviewSummaryResponse(
                ReviewScoreStatus.NO_REVIEWS,
                null,
                0
        );

        assertThat(objectMapper.writeValueAsString(available))
                .contains("\"scoreStatus\":\"AVAILABLE\"")
                .contains("\"dasiHankkiScore\":84.0")
                .contains("\"validReviewCount\":8");
        assertThat(objectMapper.writeValueAsString(insufficient))
                .contains("\"scoreStatus\":\"INSUFFICIENT_REVIEWS\"")
                .contains("\"dasiHankkiScore\":null");
        assertThat(objectMapper.writeValueAsString(empty))
                .contains("\"scoreStatus\":\"NO_REVIEWS\"")
                .contains("\"dasiHankkiScore\":null");
    }

    @Test
    void keepsPublicSummarySeparateFromMypageDto() throws Exception {
        PublicReviewSummaryResponse response = new PublicReviewSummaryResponse(
                ReviewScoreStatus.AVAILABLE,
                new BigDecimal("72.5"),
                5
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .contains("\"scoreStatus\":\"AVAILABLE\"")
                .contains("\"dasiHankkiScore\":72.5")
                .contains("\"validReviewCount\":5")
                .doesNotContain("reviewerId", "revieweeId", "revisitIntention", "impressionTag", "email", "providerId");
    }
}
