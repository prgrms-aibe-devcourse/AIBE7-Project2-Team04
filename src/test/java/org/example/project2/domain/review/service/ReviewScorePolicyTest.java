package org.example.project2.domain.review.service;

import org.example.project2.domain.review.repository.ReviewScoreAggregateProjection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ReviewScorePolicyTest {

    @Test
    void exposesAnExplicitFormulaVersionForRecalculationAudits() {
        assertThat(ReviewScorePolicy.CURRENT_VERSION).isEqualTo("DASI_HANKKI_V1");
    }

    @Test
    void calculatesTheVersionedScoreWithoutUsingTags() {
        ReviewScoreAggregateProjection aggregate = mock(ReviewScoreAggregateProjection.class);
        when(aggregate.getDefinitelyAgainCount()).thenReturn(3L);
        when(aggregate.getMaybeAgainCount()).thenReturn(0L);

        assertThat(new ReviewScorePolicy().calculate(aggregate, 3L))
                .isEqualByComparingTo("68.8");
        verify(aggregate).getDefinitelyAgainCount();
        verify(aggregate).getMaybeAgainCount();
        verifyNoMoreInteractions(aggregate);
    }
}
