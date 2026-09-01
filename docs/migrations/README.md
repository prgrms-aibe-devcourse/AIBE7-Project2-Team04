# 데이터베이스 마이그레이션 실행 절차

현재 프로젝트는 Flyway를 사용하지 않으며, 이 디렉터리의 SQL은 기존 `ddl-auto=update` PostgreSQL/Supabase 스키마를 현재 Entity 계약으로 올리는 증분 마이그레이션입니다. 운영에서는 `ddl-auto=update`에 의존하지 않고 아래 순서로 직접 적용합니다.

## 적용 대상

- PostgreSQL 15 이상
- PostGIS 확장
- pgvector 확장
- uuid-ossp 확장
- 기존 애플리케이션 테이블이 생성된 개발·운영 DB

이 SQL들은 빈 데이터베이스에 전체 테이블을 생성하는 baseline이 아닙니다. 신규 DB는 먼저 [데이터모델링의 현재 DDL](../specs/데이터모델링.md#42-테이블-생성-sql-ddl)로 기본 스키마를 생성한 뒤에도 아래 1~6번을 같은 순서로 적용해 제약 이름과 운영 인덱스를 정규화합니다.

## 변경 전 필수 조치

1. 대상 DB와 스키마가 맞는지 확인합니다. 모든 스크립트는 `public` 스키마를 대상으로 합니다.
2. 운영 DB 백업 또는 Supabase Point-in-Time Recovery 가능 여부를 확인합니다.
3. 애플리케이션 쓰기를 중지하거나 점검 모드로 전환합니다.
4. 아래 사전 점검 쿼리의 결과를 확인합니다.

```sql
SELECT current_database(), current_schema();

SELECT extname
FROM pg_extension
WHERE extname IN ('postgis', 'uuid-ossp', 'vector')
ORDER BY extname;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

## 기존 DB 적용 순서

다음 순서를 유지합니다. 이미 정상 적용된 스크립트는 다시 실행해도 같은 계약으로 수렴하도록 작성했습니다.

1. [`성향_스키마_정합성.sql`](./성향_스키마_정합성.sql)
2. [`매칭_참여자_역할_및_ID_정합성.sql`](./매칭_참여자_역할_및_ID_정합성.sql)
3. [`사용자_후기_컬럼_전환.sql`](./사용자_후기_컬럼_전환.sql)
4. [`매칭_종료_및_신고_정합성.sql`](./매칭_종료_및_신고_정합성.sql)
5. [`지역_기준_데이터.sql`](./지역_기준_데이터.sql)
6. [`현재_스키마_검증.sql`](./현재_스키마_검증.sql)

각 변경 스크립트의 트랜잭션이 오류 없이 `COMMIT`되었는지 확인한 뒤 다음 단계로 진행합니다. 마지막 검증 스크립트는 읽기 전용이며 누락이나 잘못된 데이터가 있으면 예외를 발생시킵니다.

## 1. 성향 스키마 정합성

`성향_스키마_정합성.sql`은 과거 `docs/sql`에 분리되어 있던 온보딩 상태와 V1 설문 제약을 통합하고, 현재 Enum 계약에 맞춰 다음 항목을 검증합니다.

- `users.personality_onboarding_status`
- 성향 프로필 점수 범위·설문 버전·AI 동의와 자기소개 정합성
- 설문 차원과 응답 값
- 성향 태그, 음식 카테고리, 매칭 희망 태그의 허용 값

지원하지 않는 값이 남아 있으면 `VALIDATE CONSTRAINT`에서 중단되므로, 값을 임의 변환하지 말고 해당 행의 업무 의미를 확인한 뒤 정리합니다.

## 2. 매칭 참여자와 ID 정합성

`매칭_참여자_역할_및_ID_정합성.sql`은 다음 작업을 수행합니다.

- 매칭 요청의 PostGIS GiST·지역/상태 인덱스 보장
- 구 역할 `REQUESTER`, `CANDIDATE`를 중립 역할 `PARTICIPANT`로 전환
- `matches`, `match_participants`, `chat_rooms` ID 시퀀스를 현재 최댓값 다음으로 정렬

역할 전환은 의미가 보존되는 코드 변경이지만, 적용 전에 구 역할별 행 수를 기록해 두는 것을 권장합니다.

```sql
SELECT role, COUNT(*)
FROM public.match_participants
GROUP BY role
ORDER BY role;
```

## 3. 사용자 후기 컬럼 전환

`사용자_후기_컬럼_전환.sql`은 `revisit_intention`, `impression_tag`와 현재 후기 제약·조회 인덱스를 적용하고 더 이상 사용하지 않는 `rating`, `content`를 삭제합니다.

레거시 컬럼에 값이 한 건이라도 있으면 스크립트가 중단됩니다. 기존 별점과 자유 서술은 새 재만남 의향으로 의미를 보존해 자동 변환할 수 없으므로 임의 백필하지 않습니다.

먼저 현재 컬럼 존재 여부를 확인합니다.

```sql
SELECT column_name, is_nullable, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'user_reviews'
ORDER BY ordinal_position;
```

`rating` 또는 `content`가 존재하면 해당 컬럼만 대상으로 NULL이 아닌 값의 수를 확인합니다. 값이 남아 있다면 별도 보존 정책과 백업을 확정하기 전에는 이 마이그레이션을 실행하지 않습니다.

## 4. 매칭 종료·사용자 제재·신고 정합성

`매칭_종료_및_신고_정합성.sql`은 후기 기능 이후 추가된 다음 스키마를 반영합니다.

- `matches.ended_at`과 매칭 상태/종료 시각 CHECK
- 채팅방 상태/종료 시각 CHECK
- `users.warning_count`, `BANNED` 상태
- 더 이상 사용하지 않는 `users.profile_image_key` 제거
- `reports` 테이블, 신고 카테고리 CHECK, 조회 인덱스

종료 상태인데 종료 시각이 없는 레거시 행은 시각을 추론하지 않고 중단합니다. 적용 전에 아래 결과가 0건인지 확인합니다.

```sql
SELECT id, status, matched_at, ended_at, updated_at
FROM public.matches
WHERE (status = 'MATCHED' AND ended_at IS NOT NULL)
   OR (status IN ('COMPLETED', 'CANCELLED') AND ended_at IS NULL);

SELECT id, status, created_at, closed_at
FROM public.chat_rooms
WHERE (status = 'ACTIVE' AND closed_at IS NOT NULL)
   OR (status = 'CLOSED' AND closed_at IS NULL);
```

`ended_at` 컬럼이 아직 없으면 첫 번째 조회에서 해당 컬럼을 제외하고 종료 상태 행만 확인합니다. 레거시 종료 행이 있으면 감사 가능한 이벤트나 운영 기록으로 종료 시각을 확정한 후 수동 백필합니다. `updated_at`을 근거 없이 종료 시각으로 복사하지 않습니다.

`profile_image_key` 컬럼에 값이 남아 있는 경우에도 삭제를 중단합니다. 기존 S3 객체 정리 또는 키 보존 정책을 먼저 확정해야 합니다.

## 5. 지역 기준 데이터

`지역_기준_데이터.sql`은 `src/main/resources/import.sql`과 같은 행정구역 코드·대표 좌표를 upsert합니다. 충돌 시 표시명과 대표 좌표를 현재 값으로 갱신하며 사용자의 실제 위치 데이터는 다루지 않습니다.

```sql
SELECT COUNT(*) AS region_count
FROM public.regions;
```

## 6. 최종 검증

`현재_스키마_검증.sql`은 다음 항목을 한 번에 확인합니다.

- 필수 확장과 전체 Entity 테이블 존재 여부
- 최신 컬럼 추가 및 레거시 컬럼 제거 여부
- 매칭·채팅·후기 데이터 상태 정합성
- 현재 Enum/CHECK 제약의 존재와 검증 상태
- 공간 검색·후기 조회·신고 조회 필수 인덱스
- 지역 기준 데이터 존재 여부

성공 시 다음 NOTICE가 출력됩니다.

```text
현재 Entity와 필수 운영 스키마의 정합성 검증을 통과했습니다.
```

애플리케이션을 재시작한 뒤에는 테스트 성공 여부와 별개로 로그에서 `CommandAcceptanceException` 및 Hibernate 스키마 관련 오류가 없는지 확인합니다.

## 롤백 원칙

- 제약과 인덱스 추가는 문제가 된 스크립트의 트랜잭션을 롤백한 뒤 원인을 수정합니다.
- 역할 값이나 지역 기준 데이터가 변경된 뒤에는 적용 전 백업과 기록을 기준으로 복원합니다.
- `rating`, `content`, `profile_image_key` 삭제 뒤에는 구버전 애플리케이션으로 즉시 롤백할 수 없습니다. 컬럼과 데이터가 필요한 경우 백업에서 복원하고 해당 스키마와 호환되는 애플리케이션 버전을 배포합니다.
- 운영에서 SQL을 역순으로 임의 실행하거나 삭제된 컬럼을 빈 컬럼으로만 재생성해 롤백 완료로 판단하지 않습니다.
