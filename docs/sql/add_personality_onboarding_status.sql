-- 1. 컬럼이 없으면 추가
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS personality_onboarding_status VARCHAR(20);

-- 2. 기존 NULL 데이터 보정
UPDATE users
SET personality_onboarding_status = 'NOT_STARTED'
WHERE personality_onboarding_status IS NULL;

-- 3. 기본값과 NOT NULL 설정
ALTER TABLE users
    ALTER COLUMN personality_onboarding_status SET DEFAULT 'NOT_STARTED';

ALTER TABLE users
    ALTER COLUMN personality_onboarding_status SET NOT NULL;

-- 4. CHECK 제약조건이 없을 때만 추가
DO $$
BEGIN
      IF NOT EXISTS (
          SELECT 1
          FROM pg_constraint
          WHERE conname = 'chk_users_personality_onboarding_status'
      ) THEN
ALTER TABLE users
    ADD CONSTRAINT chk_users_personality_onboarding_status
        CHECK (
            personality_onboarding_status IN (
                                              'NOT_STARTED',
                                              'SKIPPED',
                                              'COMPLETED'
                )
            );
END IF;
END
  $$;