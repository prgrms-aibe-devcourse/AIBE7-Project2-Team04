# 데이터베이스 마이그레이션 실행 절차

현재 MVP는 Flyway를 사용하지 않으므로, 운영·개발 Supabase에 애플리케이션을 배포하기 전에 아래 SQL을 순서대로 실행합니다.

후기 컬럼을 제거하기 전에 레거시 컬럼에 남은 값과 테이블 의존성을 확인합니다. 아래 조회에서 `rating`·`content` 값이 0건이 아니면 마이그레이션 스크립트가 안전을 위해 중단됩니다. 외부 객체가 컬럼을 참조하는 경우에도 `DROP COLUMN`의 기본 `RESTRICT` 동작으로 트랜잭션이 실패합니다.

```sql
SELECT COUNT(*) AS review_count
FROM public.user_reviews;

SELECT COUNT(*) AS legacy_value_count
FROM public.user_reviews
WHERE rating IS NOT NULL OR content IS NOT NULL;
```

1. Supabase SQL Editor에서 [`매칭_참여자_역할_및_ID_정합성.sql`](./매칭_참여자_역할_및_ID_정합성.sql)을 실행합니다.
2. 후기 API를 전환하기 전에 백업을 생성하고 [`사용자_후기_컬럼_전환.sql`](./사용자_후기_컬럼_전환.sql)을 실행합니다. 스크립트는 `revisit_intention`·`impression_tag`를 적용한 뒤 값이 없는 `rating`·`content`를 삭제합니다.
3. 각 스크립트가 오류 없이 커밋됐는지 확인합니다. 후기 컬럼 전환 스크립트는 반복 실행할 수 있습니다.
4. 다음 조회로 실제 인덱스와 역할 CHECK 정의를 검증합니다.

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

SELECT column_name, is_nullable, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'user_reviews'
  AND column_name IN ('revisit_intention', 'impression_tag')
ORDER BY ordinal_position;

SELECT conname, pg_get_constraintdef(oid), convalidated
FROM pg_constraint
WHERE conrelid = 'public.user_reviews'::regclass
  AND conname IN (
      'uk_user_review_match_reviewer_reviewee',
      'chk_user_reviews_distinct_users',
      'chk_user_reviews_revisit_intention_values',
      'chk_user_reviews_impression_tag_values',
      'chk_user_reviews_visibility_values'
  )
ORDER BY conname;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'user_reviews'
  AND indexname IN (
      'idx_user_reviews_reviewee_created',
      'idx_user_reviews_reviewee_visibility_created'
  )
ORDER BY indexname;

SELECT COUNT(*) AS missing_revisit_intention
FROM user_reviews
WHERE revisit_intention IS NULL;
```

정상 상태는 다음과 같습니다.

- `idx_match_requests_location`: `USING gist (location)`
- `idx_match_requests_region_status`: `USING btree (region_code, status)`
- `match_participants_role_check`: 검증된(`convalidated = true`) 제약이며 `PARTICIPANT`만 허용
- `match_participants.role`: `REQUESTER`, `CANDIDATE` 값이 없음
- `user_reviews.revisit_intention`: `NOT NULL`이며 세 고정 코드만 허용
- `user_reviews.impression_tag`: NULL 또는 네 고정 코드만 허용
- `user_reviews.visibility`: `PUBLIC` 또는 `PRIVATE`만 허용
- `user_reviews`에 `rating`·`content` 컬럼이 없음
- `user_reviews`: 후기 Unique 제약과 두 개의 reviewee 기준 복합 인덱스가 존재
- `missing_revisit_intention`: `0`

### 후기 레거시 컬럼 제거 및 롤백

`사용자_후기_컬럼_전환.sql`은 기존 `rating`·`content`에 값이 남아 있으면 중단하고, 값이 없을 때만 두 컬럼을 삭제합니다. 기존 별점과 새 재만남 의향의 의미를 임의로 변환하거나 자유 서술을 버리지 않도록 자동 백필은 수행하지 않습니다.

컬럼 삭제 전 반드시 데이터베이스 백업을 생성하고, 운영·개발 DB에서 아래 결과를 확인합니다.

```sql
SELECT COUNT(*) AS legacy_value_count
FROM public.user_reviews
WHERE rating IS NOT NULL OR content IS NOT NULL;
```

`rating`·`content`를 삭제한 뒤 구버전 애플리케이션으로 즉시 롤백할 수 없습니다. 롤백이 필요하면 컬럼과 스키마를 백업에서 복원하고, 복원된 스키마에 맞는 애플리케이션 버전을 배포합니다.

애플리케이션 시작 후에는 로그에서 `CommandAcceptanceException`과 Hibernate 스키마 생성 오류가 없는지도 확인합니다.
