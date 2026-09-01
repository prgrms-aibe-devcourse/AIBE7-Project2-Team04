-- 모든 증분 마이그레이션과 지역 기준 데이터 적용 후 실행하는 읽기 전용 검증입니다.
-- 누락이나 계약 위반이 하나라도 있으면 예외를 발생시키며 데이터를 변경하지 않습니다.

DO $$
DECLARE
    required_table TEXT;
    required_constraint TEXT;
    required_index TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        RAISE EXCEPTION 'postgis 확장이 활성화되어 있지 않습니다.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'vector 확장이 활성화되어 있지 않습니다.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp') THEN
        RAISE EXCEPTION 'uuid-ossp 확장이 활성화되어 있지 않습니다.';
    END IF;

    FOREACH required_table IN ARRAY ARRAY[
        'public.users',
        'public.refresh_tokens',
        'public.regions',
        'public.restaurants',
        'public.restaurant_embeddings',
        'public.user_location_preferences',
        'public.user_personality_profiles',
        'public.user_personality_answers',
        'public.user_personality_tags',
        'public.user_personality_embeddings',
        'public.user_food_preferences',
        'public.match_requests',
        'public.match_request_desired_personality_tags',
        'public.match_proposals',
        'public.matches',
        'public.match_participants',
        'public.chat_rooms',
        'public.chat_messages',
        'public.user_reviews',
        'public.reports'
    ]
    LOOP
        IF to_regclass(required_table) IS NULL THEN
            RAISE EXCEPTION '필수 테이블 %이(가) 없습니다.', required_table;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users'
          AND column_name = 'warning_count' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'users.warning_count NOT NULL 컬럼이 없습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users'
          AND column_name = 'profile_image_key'
    ) THEN
        RAISE EXCEPTION '더 이상 사용하지 않는 users.profile_image_key 컬럼이 남아 있습니다.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'matches'
          AND column_name = 'ended_at'
    ) THEN
        RAISE EXCEPTION 'matches.ended_at 컬럼이 없습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.matches
        WHERE (status = 'MATCHED' AND ended_at IS NOT NULL)
           OR (status IN ('COMPLETED', 'CANCELLED') AND ended_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'matches.status와 ended_at 데이터가 일치하지 않습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.chat_rooms
        WHERE (status = 'ACTIVE' AND closed_at IS NOT NULL)
           OR (status = 'CLOSED' AND closed_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'chat_rooms.status와 closed_at 데이터가 일치하지 않습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.match_participants
        WHERE role <> 'PARTICIPANT'
    ) THEN
        RAISE EXCEPTION 'match_participants에 구 역할 값이 남아 있습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_reviews'
          AND column_name IN ('rating', 'content')
    ) THEN
        RAISE EXCEPTION 'user_reviews에 레거시 rating/content 컬럼이 남아 있습니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.user_reviews
        WHERE revisit_intention IS NULL
    ) THEN
        RAISE EXCEPTION 'revisit_intention이 없는 후기 행이 있습니다.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.regions) THEN
        RAISE EXCEPTION 'regions 기준 데이터가 비어 있습니다.';
    END IF;

    FOREACH required_constraint IN ARRAY ARRAY[
        'chk_users_personality_onboarding_status',
        'chk_users_status_values',
        'chk_user_personality_profiles_v1_version',
        'chk_user_personality_profiles_v1_state',
        'chk_user_personality_answers_v1_dimension',
        'chk_user_personality_answers_v1_value',
        'chk_user_personality_tags_values',
        'chk_user_food_preferences_values',
        'chk_match_request_desired_personality_tags_values',
        'match_participants_role_check',
        'chk_matches_status_ended_at',
        'chk_chat_rooms_status_closed_at',
        'uk_user_review_match_reviewer_reviewee',
        'chk_user_reviews_distinct_users',
        'chk_user_reviews_revisit_intention_values',
        'chk_user_reviews_impression_tag_values',
        'chk_user_reviews_visibility_values',
        'chk_reports_category_values',
        'fk_reports_reporter',
        'fk_reports_reported',
        'fk_reports_match'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = required_constraint
              AND convalidated
        ) THEN
            RAISE EXCEPTION '검증된 필수 제약 %이(가) 없습니다.', required_constraint;
        END IF;
    END LOOP;

    FOREACH required_index IN ARRAY ARRAY[
        'idx_match_requests_location',
        'idx_match_requests_region_status',
        'idx_chat_messages_room_created',
        'idx_user_reviews_reviewee_created',
        'idx_user_reviews_reviewee_visibility_created',
        'idx_reports_reported',
        'idx_reports_match'
    ]
    LOOP
        IF to_regclass('public.' || required_index) IS NULL THEN
            RAISE EXCEPTION '필수 인덱스 %이(가) 없습니다.', required_index;
        END IF;
    END LOOP;

    RAISE NOTICE '현재 Entity와 필수 운영 스키마의 정합성 검증을 통과했습니다.';
END
$$;
