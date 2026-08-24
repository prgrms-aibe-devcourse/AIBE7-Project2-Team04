package org.example.project2.domain.personality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project2.global.entity.BaseEntity;
import org.hibernate.annotations.Check;

@Table(name = "user_personality_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_personality_answer",
                columnNames = {"user_id", "question_code"}),
        indexes = @Index(name = "idx_user_personality_answers_user", columnList = "user_id"))
@Entity
@Check(constraints = "answer_value BETWEEN 1 AND 5")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserPersonalityAnswer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserPersonalityProfile profile;

    @Column(name = "question_code", nullable = false, length = 100)
    private String questionCode;

    @Column(name = "answer_value", nullable = false)
    private short answerValue;
}
