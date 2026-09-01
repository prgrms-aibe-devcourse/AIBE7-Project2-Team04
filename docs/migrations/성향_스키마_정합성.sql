-- ddl-auto=update로 생성된 기존 PostgreSQL 스키마에 현재 성향 계약의
-- 컬럼 기본값과 CHECK 제약을 반복 적용할 수 있도록 정합화합니다.
-- 지원하지 않는 기존 값이 있으면 VALIDATE 단계에서 중단됩니다.

DO $$
DECLARE
    required_table TEXT;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'public.users',
        'public.user_personality_profiles',
        'public.user_personality_answers',
        'public.user_personality_tags',
        'public.user_food_preferences',
        'public.match_request_desired_personality_tags'
    ]
    LOOP
        IF to_regclass(required_table) IS NULL THEN
            RAISE EXCEPTION '% 테이블이 없어 성향 스키마 정합화를 적용할 수 없습니다.', required_table;
        END IF;
    END LOOP;
END
$$;

BEGIN;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS personality_onboarding_status VARCHAR(20);

UPDATE public.users
SET personality_onboarding_status = 'NOT_STARTED'
WHERE personality_onboarding_status IS NULL;

ALTER TABLE public.users
    ALTER COLUMN personality_onboarding_status SET DEFAULT 'NOT_STARTED',
    ALTER COLUMN personality_onboarding_status SET NOT NULL;

ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS chk_users_personality_onboarding_status;

ALTER TABLE public.users
    ADD CONSTRAINT chk_users_personality_onboarding_status
    CHECK (personality_onboarding_status IN ('NOT_STARTED', 'SKIPPED', 'COMPLETED'))
    NOT VALID;

ALTER TABLE public.users
    VALIDATE CONSTRAINT chk_users_personality_onboarding_status;

ALTER TABLE public.user_personality_profiles
    DROP CONSTRAINT IF EXISTS chk_user_personality_profiles_v1_version,
    DROP CONSTRAINT IF EXISTS chk_user_personality_profiles_v1_state;

ALTER TABLE public.user_personality_profiles
    ADD CONSTRAINT chk_user_personality_profiles_v1_version
        CHECK (questionnaire_version = 'MEAL_PERSONALITY_V1') NOT VALID,
    ADD CONSTRAINT chk_user_personality_profiles_v1_state
        CHECK (
            conversation_level BETWEEN 0 AND 100
            AND meal_pace BETWEEN 0 AND 100
            AND planning_style BETWEEN 0 AND 100
            AND novelty_preference BETWEEN 0 AND 100
            AND (self_description IS NULL OR char_length(self_description) <= 300)
            AND (ai_analysis_consent OR self_description IS NULL)
        ) NOT VALID;

ALTER TABLE public.user_personality_profiles
    VALIDATE CONSTRAINT chk_user_personality_profiles_v1_version;

ALTER TABLE public.user_personality_profiles
    VALIDATE CONSTRAINT chk_user_personality_profiles_v1_state;

ALTER TABLE public.user_personality_answers
    DROP CONSTRAINT IF EXISTS chk_user_personality_answers_v1_dimension,
    DROP CONSTRAINT IF EXISTS chk_user_personality_answers_v1_value;

ALTER TABLE public.user_personality_answers
    ADD CONSTRAINT chk_user_personality_answers_v1_dimension
        CHECK (question_code IN (
            'CONVERSATION_LEVEL',
            'MEAL_PACE',
            'PLANNING_STYLE',
            'NOVELTY_PREFERENCE'
        )) NOT VALID,
    ADD CONSTRAINT chk_user_personality_answers_v1_value
        CHECK (answer_value IN (1, 3, 5)) NOT VALID;

ALTER TABLE public.user_personality_answers
    VALIDATE CONSTRAINT chk_user_personality_answers_v1_dimension;

ALTER TABLE public.user_personality_answers
    VALIDATE CONSTRAINT chk_user_personality_answers_v1_value;

ALTER TABLE public.user_personality_tags
    DROP CONSTRAINT IF EXISTS chk_user_personality_tags_values;

ALTER TABLE public.user_personality_tags
    ADD CONSTRAINT chk_user_personality_tags_values
    CHECK (tag_code IN (
        'INITIATES_CONVERSATION',
        'GOOD_LISTENER',
        'FOOD_TALK',
        'LIGHT_CHAT',
        'DEEP_TALK',
        'COMFORTABLE_SILENCE',
        'CALM_ATMOSPHERE',
        'CHEERFUL_ATMOSPHERE',
        'ACTIVE_ATMOSPHERE',
        'SHARE_DISHES',
        'TAKE_FOOD_PHOTOS',
        'ENJOY_DESSERT',
        'FOCUS_ON_MEAL'
    )) NOT VALID;

ALTER TABLE public.user_personality_tags
    VALIDATE CONSTRAINT chk_user_personality_tags_values;

ALTER TABLE public.user_food_preferences
    DROP CONSTRAINT IF EXISTS chk_user_food_preferences_values;

ALTER TABLE public.user_food_preferences
    ADD CONSTRAINT chk_user_food_preferences_values
    CHECK (food_category IN (
        'KOREAN',
        'JAPANESE',
        'CHINESE',
        'WESTERN',
        'SOUTHEAST_ASIAN',
        'SNACK',
        'FAST_FOOD',
        'CAFE_DESSERT'
    )) NOT VALID;

ALTER TABLE public.user_food_preferences
    VALIDATE CONSTRAINT chk_user_food_preferences_values;

ALTER TABLE public.match_request_desired_personality_tags
    DROP CONSTRAINT IF EXISTS chk_match_request_desired_personality_tags_values;

ALTER TABLE public.match_request_desired_personality_tags
    ADD CONSTRAINT chk_match_request_desired_personality_tags_values
    CHECK (tag_code IN (
        'INITIATES_CONVERSATION',
        'GOOD_LISTENER',
        'FOOD_TALK',
        'LIGHT_CHAT',
        'DEEP_TALK',
        'COMFORTABLE_SILENCE',
        'CALM_ATMOSPHERE',
        'CHEERFUL_ATMOSPHERE',
        'ACTIVE_ATMOSPHERE',
        'SHARE_DISHES',
        'TAKE_FOOD_PHOTOS',
        'ENJOY_DESSERT',
        'FOCUS_ON_MEAL'
    )) NOT VALID;

ALTER TABLE public.match_request_desired_personality_tags
    VALIDATE CONSTRAINT chk_match_request_desired_personality_tags_values;

COMMIT;
