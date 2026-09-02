-- 후기 마이그레이션 이후 추가된 매칭 종료 상태, 채팅방 종료 상태,
-- 사용자 제재 상태와 신고 테이블을 기존 PostgreSQL 스키마에 반영합니다.
-- 데이터 손실을 피하기 위해 의미를 추론할 수 없는 기존 값이 있으면 중단합니다.

DO $$
DECLARE
    required_table TEXT;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'public.users',
        'public.matches',
        'public.chat_rooms'
    ]
    LOOP
        IF to_regclass(required_table) IS NULL THEN
            RAISE EXCEPTION '% 테이블이 없어 운영 상태 정합화를 적용할 수 없습니다.', required_table;
        END IF;
    END LOOP;
END
$$;

BEGIN;

-- 더 이상 사용하지 않는 S3 객체 키 컬럼은 값이 없을 때만 제거합니다.
DO $$
DECLARE
    has_profile_image_key BOOLEAN;
    has_profile_image_key_value BOOLEAN := FALSE;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'profile_image_key'
    ) INTO has_profile_image_key;

    IF has_profile_image_key THEN
        EXECUTE 'SELECT EXISTS (
                     SELECT 1
                     FROM public.users
                     WHERE profile_image_key IS NOT NULL
                 )'
            INTO has_profile_image_key_value;
    END IF;

    IF has_profile_image_key_value THEN
        RAISE EXCEPTION 'users.profile_image_key에 값이 남아 있어 컬럼을 삭제할 수 없습니다.';
    END IF;
END
$$;

ALTER TABLE public.users
    DROP COLUMN IF EXISTS profile_image_key,
    ADD COLUMN IF NOT EXISTS warning_count INTEGER;

UPDATE public.users
SET warning_count = 0
WHERE warning_count IS NULL;

ALTER TABLE public.users
    ALTER COLUMN warning_count SET DEFAULT 0,
    ALTER COLUMN warning_count SET NOT NULL;

-- BANNED를 허용하지 않는 구 status CHECK를 제거하고 현재 Enum 계약으로 교체합니다.
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.users'::regclass
          AND contype = 'c'
          AND pg_get_expr(conbin, conrelid)
              ~ '(^|[^[:alnum:]_])status([^[:alnum:]_]|$)'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.users DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END
$$;

ALTER TABLE public.users
    ADD CONSTRAINT chk_users_status_values
    CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'BANNED'))
    NOT VALID;

ALTER TABLE public.users
    VALIDATE CONSTRAINT chk_users_status_values;

ALTER TABLE public.matches
    ADD COLUMN IF NOT EXISTS ended_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.matches
        WHERE (status = 'MATCHED' AND ended_at IS NOT NULL)
           OR (status IN ('COMPLETED', 'CANCELLED') AND ended_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'matches.status와 ended_at이 일치하지 않습니다. 종료 시각을 확인해 수동 보정한 뒤 다시 실행하세요.';
    END IF;
END
$$;

ALTER TABLE public.matches
    DROP CONSTRAINT IF EXISTS chk_matches_status_ended_at;

ALTER TABLE public.matches
    ADD CONSTRAINT chk_matches_status_ended_at
    CHECK (
        (status = 'MATCHED' AND ended_at IS NULL)
        OR (status IN ('COMPLETED', 'CANCELLED') AND ended_at IS NOT NULL)
    ) NOT VALID;

ALTER TABLE public.matches
    VALIDATE CONSTRAINT chk_matches_status_ended_at;

ALTER TABLE public.chat_rooms
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.chat_rooms
        WHERE (status = 'ACTIVE' AND closed_at IS NOT NULL)
           OR (status = 'CLOSED' AND closed_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'chat_rooms.status와 closed_at이 일치하지 않습니다. 종료 시각을 확인해 수동 보정한 뒤 다시 실행하세요.';
    END IF;
END
$$;

ALTER TABLE public.chat_rooms
    DROP CONSTRAINT IF EXISTS chk_chat_rooms_status_closed_at;

ALTER TABLE public.chat_rooms
    ADD CONSTRAINT chk_chat_rooms_status_closed_at
    CHECK (
        (status = 'ACTIVE' AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL)
    ) NOT VALID;

ALTER TABLE public.chat_rooms
    VALIDATE CONSTRAINT chk_chat_rooms_status_closed_at;

CREATE TABLE IF NOT EXISTS public.reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id UUID NOT NULL,
    reported_user_id UUID NOT NULL,
    match_id BIGINT NOT NULL,
    category VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reports_reporter
        FOREIGN KEY (reporter_id) REFERENCES public.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reported
        FOREIGN KEY (reported_user_id) REFERENCES public.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_match
        FOREIGN KEY (match_id) REFERENCES public.matches(id) ON DELETE CASCADE
);

ALTER TABLE public.reports
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE public.reports
SET status = 'PENDING'
WHERE status IS NULL;

ALTER TABLE public.reports
    ALTER COLUMN status SET DEFAULT 'PENDING',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE public.reports
    DROP CONSTRAINT IF EXISTS chk_reports_status_values;

ALTER TABLE public.reports
    ADD CONSTRAINT chk_reports_status_values
    CHECK (status IN ('PENDING', 'DISMISSED', 'ACTIONED'))
    NOT VALID;

ALTER TABLE public.reports
    VALIDATE CONSTRAINT chk_reports_status_values;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_reporter_target_match
    ON public.reports (reporter_id, reported_user_id, match_id);

-- ddl-auto가 임의 이름과 기본 삭제 정책으로 만든 FK도 문서의 CASCADE 계약으로 교체합니다.
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT DISTINCT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_attribute column_info
          ON column_info.attrelid = constraint_info.conrelid
         AND column_info.attnum = ANY (constraint_info.conkey)
        WHERE constraint_info.conrelid = 'public.reports'::regclass
          AND constraint_info.contype = 'f'
          AND column_info.attname IN ('reporter_id', 'reported_user_id', 'match_id')
    LOOP
        EXECUTE format(
            'ALTER TABLE public.reports DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END
$$;

ALTER TABLE public.reports
    ADD CONSTRAINT fk_reports_reporter
        FOREIGN KEY (reporter_id) REFERENCES public.users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_reports_reported
        FOREIGN KEY (reported_user_id) REFERENCES public.users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_reports_match
        FOREIGN KEY (match_id) REFERENCES public.matches(id) ON DELETE CASCADE;

ALTER TABLE public.reports
    DROP CONSTRAINT IF EXISTS chk_reports_category_values;

ALTER TABLE public.reports
    ADD CONSTRAINT chk_reports_category_values
    CHECK (category IN ('NO_SHOW', 'ABUSE', 'SPAM', 'MISINFORMATION'))
    NOT VALID;

ALTER TABLE public.reports

-- ddl-auto가 임의 이름과 기본 삭제 정책으로 만든 FK도 문서의 CASCADE 계약으로 교체합니다.
DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT DISTINCT constraint_info.conname
        FROM pg_constraint constraint_info
        JOIN pg_attribute column_info
          ON column_info.attrelid = constraint_info.conrelid
         AND column_info.attnum = ANY (constraint_info.conkey)
        WHERE constraint_info.conrelid = 'public.reports'::regclass
          AND constraint_info.contype = 'f'
          AND column_info.attname IN ('reporter_id', 'reported_user_id', 'match_id')
    LOOP
        EXECUTE format(
            'ALTER TABLE public.reports DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END
$$;

ALTER TABLE public.reports
    ADD CONSTRAINT fk_reports_reporter
        FOREIGN KEY (reporter_id) REFERENCES public.users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_reports_reported
        FOREIGN KEY (reported_user_id) REFERENCES public.users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_reports_match
        FOREIGN KEY (match_id) REFERENCES public.matches(id) ON DELETE CASCADE;

ALTER TABLE public.reports
    DROP CONSTRAINT IF EXISTS chk_reports_category_values;

ALTER TABLE public.reports
    ADD CONSTRAINT chk_reports_category_values
    CHECK (category IN ('NO_SHOW', 'ABUSE', 'SPAM', 'MISINFORMATION'))
    NOT VALID;

ALTER TABLE public.reports
    VALIDATE CONSTRAINT chk_reports_category_values;

CREATE INDEX IF NOT EXISTS idx_reports_reported
    ON public.reports (reported_user_id);

CREATE INDEX IF NOT EXISTS idx_reports_match
    ON public.reports (match_id);

-- PostGIS 공간 거리 검색 (ST_DWithin) 고속화를 위한 GiST 인덱스
CREATE INDEX IF NOT EXISTS idx_match_requests_location_gist
    ON public.match_requests USING GIST (location);

COMMIT;
