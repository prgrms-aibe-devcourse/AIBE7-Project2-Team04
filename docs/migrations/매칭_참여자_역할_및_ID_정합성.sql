-- 기존 ddl-auto=update 데이터베이스에 배포마다 반복 실행할 수 있는 호환성 마이그레이션입니다.
-- 매칭 요청 인덱스, 참여자 역할, 새 매칭 테이블의 ID 시퀀스를 현재 모델과 맞춥니다.

DO $$
DECLARE
    required_table TEXT;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'public.match_requests',
        'public.matches',
        'public.match_participants',
        'public.chat_rooms'
    ]
    LOOP
        IF to_regclass(required_table) IS NULL THEN
            RAISE EXCEPTION '% 테이블이 없어 매칭 스키마 정합화를 적용할 수 없습니다.', required_table;
        END IF;
    END LOOP;
END
$$;

BEGIN;

CREATE INDEX IF NOT EXISTS idx_match_requests_location
    ON public.match_requests USING GIST (location);

CREATE INDEX IF NOT EXISTS idx_match_requests_region_status
    ON public.match_requests (region_code, status);

ALTER TABLE public.match_participants
    DROP CONSTRAINT IF EXISTS match_participants_role_check;

UPDATE public.match_participants
SET role = 'PARTICIPANT'
WHERE role IN ('REQUESTER', 'CANDIDATE');

ALTER TABLE public.match_participants
    ADD CONSTRAINT match_participants_role_check
    CHECK (role = 'PARTICIPANT')
    NOT VALID;

ALTER TABLE public.match_participants
    VALIDATE CONSTRAINT match_participants_role_check;

SELECT setval(
    pg_get_serial_sequence('public.matches', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM public.matches), 0) + 1, 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.match_participants', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM public.match_participants), 0) + 1, 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('public.chat_rooms', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM public.chat_rooms), 0) + 1, 1),
    false
);

COMMIT;
