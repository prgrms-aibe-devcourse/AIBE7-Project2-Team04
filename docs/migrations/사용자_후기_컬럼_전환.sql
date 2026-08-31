-- 사용자 후기의 새 저장 계약을 적용하고 사용하지 않는 rating/content 컬럼을 제거합니다.
--
-- 이 스크립트는 레거시 컬럼에 값이 남아 있으면 중단합니다.
-- 컬럼 삭제는 되돌릴 수 없으므로 운영 적용 전 백업과 아래 사전 검증을 완료해야 합니다.

DO $$
DECLARE
    has_rating BOOLEAN;
    has_content BOOLEAN;
    has_legacy_values BOOLEAN := FALSE;
BEGIN
    IF to_regclass('public.user_reviews') IS NULL THEN
        RAISE EXCEPTION 'user_reviews 테이블이 없어 후기 컬럼 전환을 적용할 수 없습니다.';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_reviews'
          AND column_name = 'rating'
    ) INTO has_rating;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_reviews'
          AND column_name = 'content'
    ) INTO has_content;

    IF has_rating AND has_content THEN
        EXECUTE 'SELECT EXISTS (
                     SELECT 1
                     FROM public.user_reviews
                     WHERE rating IS NOT NULL OR content IS NOT NULL
                 )'
            INTO has_legacy_values;
    ELSIF has_rating THEN
        EXECUTE 'SELECT EXISTS (
                     SELECT 1
                     FROM public.user_reviews
                     WHERE rating IS NOT NULL
                 )'
            INTO has_legacy_values;
    ELSIF has_content THEN
        EXECUTE 'SELECT EXISTS (
                     SELECT 1
                     FROM public.user_reviews
                     WHERE content IS NOT NULL
                 )'
            INTO has_legacy_values;
    END IF;

    IF has_legacy_values THEN
        RAISE EXCEPTION 'user_reviews.rating 또는 content에 보존되지 않은 데이터가 있어 컬럼을 삭제할 수 없습니다.';
    END IF;
END
$$;

BEGIN;

ALTER TABLE public.user_reviews
    ADD COLUMN IF NOT EXISTS revisit_intention VARCHAR(30),
    ADD COLUMN IF NOT EXISTS impression_tag VARCHAR(30);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.user_reviews
        WHERE revisit_intention IS NULL
    ) THEN
        RAISE EXCEPTION 'revisit_intention이 없는 후기 행이 있어 NOT NULL을 적용할 수 없습니다.';
    END IF;
END
$$;

ALTER TABLE public.user_reviews
    ALTER COLUMN revisit_intention SET NOT NULL;

ALTER TABLE public.user_reviews
    DROP CONSTRAINT IF EXISTS chk_user_reviews_legacy_rating;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.user_reviews'::regclass
          AND conname = 'chk_user_reviews_revisit_intention_values'
    ) THEN
        ALTER TABLE public.user_reviews
            ADD CONSTRAINT chk_user_reviews_revisit_intention_values
            CHECK (revisit_intention IN ('DEFINITELY_AGAIN', 'MAYBE_AGAIN', 'ENOUGH_FOR_NOW'))
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.user_reviews'::regclass
          AND conname = 'chk_user_reviews_impression_tag_values'
    ) THEN
        ALTER TABLE public.user_reviews
            ADD CONSTRAINT chk_user_reviews_impression_tag_values
            CHECK (impression_tag IS NULL OR impression_tag IN (
                'PUNCTUAL',
                'COMFORTABLE_CONVERSATION',
                'CONSIDERATE',
                'ACTIVE_PARTICIPATION'
            ))
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.user_reviews'::regclass
          AND conname = 'chk_user_reviews_distinct_users'
    ) THEN
        ALTER TABLE public.user_reviews
            ADD CONSTRAINT chk_user_reviews_distinct_users
            CHECK (reviewer_id <> reviewee_id)
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.user_reviews'::regclass
          AND conname = 'chk_user_reviews_visibility_values'
    ) THEN
        ALTER TABLE public.user_reviews
            ADD CONSTRAINT chk_user_reviews_visibility_values
            CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
            NOT VALID;
    END IF;
END
$$;

ALTER TABLE public.user_reviews
    VALIDATE CONSTRAINT chk_user_reviews_revisit_intention_values;

ALTER TABLE public.user_reviews
    VALIDATE CONSTRAINT chk_user_reviews_impression_tag_values;

ALTER TABLE public.user_reviews
    VALIDATE CONSTRAINT chk_user_reviews_distinct_users;

ALTER TABLE public.user_reviews
    VALIDATE CONSTRAINT chk_user_reviews_visibility_values;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.user_reviews'::regclass
          AND conname = 'uk_user_review_match_reviewer_reviewee'
    ) THEN
        RAISE EXCEPTION '후기 중복 방지 Unique 제약이 없어 컬럼 전환을 중단합니다.';
    END IF;
END
$$;

-- 사전 검증을 통과한 경우에만 레거시 컬럼을 삭제합니다.
ALTER TABLE public.user_reviews
    DROP COLUMN IF EXISTS rating;

ALTER TABLE public.user_reviews
    DROP COLUMN IF EXISTS content;

CREATE INDEX IF NOT EXISTS idx_user_reviews_reviewee_created
    ON public.user_reviews (reviewee_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_user_reviews_reviewee_visibility_created
    ON public.user_reviews (reviewee_id, visibility, created_at DESC, id DESC);

COMMIT;

-- 롤백은 삭제된 컬럼의 백업 또는 스키마 백업을 복원하는 방식으로만 수행합니다.
-- 컬럼 삭제 후에는 rating/content를 참조하는 구버전 애플리케이션으로 바로 되돌릴 수 없습니다.
