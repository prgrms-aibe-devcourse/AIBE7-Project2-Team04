package org.example.project2.domain.review.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewOptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesStableRevisitIntentionCodesAndKoreanLabels() {
        assertThat(RevisitIntention.values())
                .extracting(RevisitIntention::getCode)
                .containsExactly("DEFINITELY_AGAIN", "MAYBE_AGAIN", "ENOUGH_FOR_NOW");
        assertThat(RevisitIntention.DEFINITELY_AGAIN.getLabel()).isEqualTo("꼭 또 보고 싶어요");
        assertThat(RevisitIntention.MAYBE_AGAIN.getLabel()).isEqualTo("기회가 되면 좋아요");
        assertThat(RevisitIntention.ENOUGH_FOR_NOW.getLabel()).isEqualTo("이번 만남으로 충분해요");
    }

    @Test
    void exposesStableImpressionTagCodesAndKoreanLabels() {
        assertThat(ImpressionTag.values())
                .extracting(ImpressionTag::getCode)
                .containsExactly(
                        "PUNCTUAL",
                        "COMFORTABLE_CONVERSATION",
                        "CONSIDERATE",
                        "ACTIVE_PARTICIPATION"
                );
        assertThat(ImpressionTag.PUNCTUAL.getLabel()).isEqualTo("시간 약속");
        assertThat(ImpressionTag.COMFORTABLE_CONVERSATION.getLabel()).isEqualTo("편안한 대화");
        assertThat(ImpressionTag.CONSIDERATE.getLabel()).isEqualTo("배려");
        assertThat(ImpressionTag.ACTIVE_PARTICIPATION.getLabel()).isEqualTo("적극적인 참여");
    }

    @Test
    void acceptsOnlyTheExactStableCodes() {
        assertThat(RevisitIntention.fromCode("DEFINITELY_AGAIN"))
                .isEqualTo(RevisitIntention.DEFINITELY_AGAIN);
        assertThat(ImpressionTag.fromCode("PUNCTUAL"))
                .isEqualTo(ImpressionTag.PUNCTUAL);

        assertThatThrownBy(() -> RevisitIntention.fromCode("definitely_again"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 재만남 의향 코드입니다.");
        assertThatThrownBy(() -> ImpressionTag.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 인상 태그 코드입니다.");
    }

    @Test
    void serializesOnlyTheStableCodeForApiPayloads() throws Exception {
        assertThat(objectMapper.writeValueAsString(RevisitIntention.DEFINITELY_AGAIN))
                .isEqualTo("\"DEFINITELY_AGAIN\"");
        assertThat(objectMapper.writeValueAsString(ImpressionTag.PUNCTUAL))
                .isEqualTo("\"PUNCTUAL\"");
    }
}
