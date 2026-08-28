# 데이터베이스 마이그레이션 실행 절차

현재 MVP는 Flyway를 사용하지 않으므로, 운영·개발 Supabase에 애플리케이션을 배포하기 전에 아래 SQL을 순서대로 실행합니다.

1. Supabase SQL Editor에서 [`매칭_참여자_역할_및_ID_정합성.sql`](./매칭_참여자_역할_및_ID_정합성.sql)을 실행합니다.
2. 스크립트가 오류 없이 커밋됐는지 확인합니다. 이 스크립트는 반복 실행할 수 있습니다.
3. 다음 조회로 실제 인덱스와 역할 CHECK 정의를 검증합니다.

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'match_requests'
  AND indexname IN (
      'idx_match_requests_location',
      'idx_match_requests_region_status'
  )
ORDER BY indexname;

SELECT conname, pg_get_constraintdef(oid), convalidated
FROM pg_constraint
WHERE conrelid = 'public.match_participants'::regclass
  AND conname = 'match_participants_role_check';

SELECT role, COUNT(*)
FROM match_participants
GROUP BY role
ORDER BY role;
```

정상 상태는 다음과 같습니다.

- `idx_match_requests_location`: `USING gist (location)`
- `idx_match_requests_region_status`: `USING btree (region_code, status)`
- `match_participants_role_check`: 검증된(`convalidated = true`) 제약이며 `PARTICIPANT`만 허용
- `match_participants.role`: `REQUESTER`, `CANDIDATE` 값이 없음

애플리케이션 시작 후에는 로그에서 `CommandAcceptanceException`과 Hibernate 스키마 생성 오류가 없는지도 확인합니다.
