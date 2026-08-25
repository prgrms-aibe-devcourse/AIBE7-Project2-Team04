package org.example.project2.domain.personality.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "user_personality_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_personality_answer",
                columnNames = {"user_id", "question_code"}),
        indexes = @Index(name = "idx_user_personality_answers_user", columnList = "user_id"))
@Entity
@Check(constraints = "answer_value IN (1, 3, 5)")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserPersonalityAnswer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserPersonalityProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_code", nullable = false, length = 100)
    private PersonalityDimension questionCode;

    @Column(name = "answer_value", nullable = false)
    private short answerValue;
}
