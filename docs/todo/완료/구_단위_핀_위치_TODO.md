# 구 단위 활동지역 및 지도 핀 위치 TODO

## 원칙

- Geolocation API는 필수로 사용하지 않는다.
- 사용자 계정에는 실시간 위치나 상세 좌표를 저장하지 않는다.
- 기본 활동지역은 구 단위 행정구역 코드와 표시명으로 저장한다.
- 실시간 1:1 매칭 요청의 상세 위치는 사용자가 지도에서 확정한 핀으로 저장한다.
- 모든 거리 표현은 실제 사용자 위치가 아니라 선택 핀 기준임을 명시한다.

## 1. 행정구역과 지도

- [x] 서비스에서 지원할 5자리 시·군·구 코드·표시명·대표 좌표 기준 데이터 준비
- [x] `GET /regions` 목록 API와 시·도별 필터 구현
- [x] 최초 이용 시 구 선택 UI 구현
- [x] 저장된 구의 대표 좌표를 중심으로 Kakao Maps 지도 초기화
- [x] 지도 클릭과 마커 이동으로 위도·경도 확정
- [x] 주소·장소 검색 결과로 핀 이동 기능 구현
- [x] 핀 확정 전 선택 위치와 "선택한 위치 기준" 안내 표시

## 2. API와 검증

- [x] `GET/PUT/DELETE /users/me/preferred-region` 구현
- [x] 구 단위 `regionCode`가 서버 지원 목록에 존재하는지 검증
- [x] Kakao 좌표→행정구역 API 또는 행정구역 경계 데이터로 핀의 구 소속 검증
- [x] 핀이 선택 구를 벗어나면 `LOCATION_002` 반환
- [x] 클라이언트의 `regionName`을 신뢰하지 않고 서버 기준 데이터로 정규화
- [x] 위도 -90~90, 경도 -180~180 범위 검증
- [x] JTS Point를 경도(`x`), 위도(`y`) 순서와 SRID 4326으로 생성

## 3. 데이터베이스

- [x] `user_location_preferences` Entity·Repository·마이그레이션 구현
- [x] `match_requests`에 `region_code`, `region_name`, `location_name` 추가
- [x] `match_requests`의 `region_code + status` B-Tree 인덱스와 위치 GIST 인덱스 적용
- [x] 기존 위치 데이터가 있다면 구 코드 역지오코딩 및 마이그레이션 정책 수립

## 4. 개인정보와 UX

- [x] 위치 기반 서비스 동의 전 지역·핀 저장 차단
- [x] 동의 철회 시 기본 활동지역 삭제
- [x] 기존 매칭 요청 핀의 보존 기간과 삭제 정책 확정
- [x] 상대방에게 매칭 요청 핀의 정밀 좌표를 언제 공개할지 정책 확정
- [x] 구 경계 부근에서 핀 검증 실패 시 구 변경 또는 핀 재지정 안내

## 5. 검증

- [x] 저장된 구 대표 좌표로 지도 초기화 테스트
- [x] 구 내부·외부·경계 핀 검증 테스트
- [x] `ST_DWithin` 반경 검색과 `ST_Distance` 선택 핀 거리 테스트
- [x] 위치 동의 없음·철회 후 `LOCATION_001` 테스트
- [x] Entity와 설정 구현 후 `gradlew.bat compileJava` 및 `gradlew.bat test` 실행
- [x] 테스트 로그에서 `CommandAcceptanceException`과 Hibernate DDL 오류가 없는지 확인
