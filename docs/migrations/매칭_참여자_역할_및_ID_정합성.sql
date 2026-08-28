-- 기존 ddl-auto=update 데이터베이스에서 한 번만 실행하는 호환성 마이그레이션입니다.
-- 구 역할 값을 중립 역할로 변환하고, 새 매칭 테이블의 ID 시퀀스를 현재 데이터 뒤로 맞춥니다.
BEGIN;

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
