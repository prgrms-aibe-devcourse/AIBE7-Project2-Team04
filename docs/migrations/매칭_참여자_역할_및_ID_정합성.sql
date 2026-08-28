-- 기존 ddl-auto=update 데이터베이스에 배포마다 반복 실행할 수 있는 호환성 마이그레이션입니다.
-- 매칭 요청 인덱스, 참여자 역할, 새 매칭 테이블의 ID 시퀀스를 현재 모델과 맞춥니다.
BEGIN;

CREATE INDEX IF NOT EXISTS idx_match_requests_location
    ON match_requests USING GIST (location);

CREATE INDEX IF NOT EXISTS idx_match_requests_region_status
    ON match_requests (region_code, status);

ALTER TABLE match_participants
    DROP CONSTRAINT IF EXISTS match_participants_role_check;

UPDATE match_participants
SET role = 'PARTICIPANT'
WHERE role IN ('REQUESTER', 'CANDIDATE');

ALTER TABLE match_participants
    ADD CONSTRAINT match_participants_role_check
    CHECK (role IN ('PARTICIPANT'));

SELECT setval(
    pg_get_serial_sequence('matches', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM matches), 0) + 1, 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('match_participants', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM match_participants), 0) + 1, 1),
    false
);

SELECT setval(
    pg_get_serial_sequence('chat_rooms', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM chat_rooms), 0) + 1, 1),
    false
);

COMMIT;
