-- MEAL_PERSONALITY_V1 계약을 기존 PostgreSQL 개발 DB에 수동 반영한다.
-- 기존 데이터에 지원하지 않는 값이 있으면 제약조건 추가가 실패하므로 먼저 데이터를 정리한다.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_user_personality_profiles_v1_version'
    ) THEN
        ALTER TABLE user_personality_profiles
            ADD CONSTRAINT chk_user_personality_profiles_v1_version
            CHECK (questionnaire_version IN ('MEAL_PERSONALITY_V1'));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_user_personality_answers_v1_dimension'
    ) THEN
        ALTER TABLE user_personality_answers
            ADD CONSTRAINT chk_user_personality_answers_v1_dimension
            CHECK (question_code IN (
                'CONVERSATION_LEVEL',
                'MEAL_PACE',
                'PLANNING_STYLE',
                'NOVELTY_PREFERENCE'
            ));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_user_personality_answers_v1_value'
    ) THEN
        ALTER TABLE user_personality_answers
            ADD CONSTRAINT chk_user_personality_answers_v1_value
            CHECK (answer_value IN (1, 3, 5));
    END IF;
END
$$;
