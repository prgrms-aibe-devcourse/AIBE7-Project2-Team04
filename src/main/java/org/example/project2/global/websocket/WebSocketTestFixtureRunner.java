package org.example.project2.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <h1>웹소켓 채팅 개발용 임시 테스트 데이터 런너</h1>
 *
 * <p><strong>[주의] 이 클래스는 개발 및 테스트 완료 후 반드시 제거하거나 @Component 주석을 해제해야 합니다.</strong></p>
 *
 * <p><strong>역할:</strong></p>
 * <ul>
 *   <li>서버 구동 시점에 로컬 테스트용 계정(usera, userb)을 생성합니다.</li>
 *   <li>웹소켓 채팅 인터셉터의 '참여자 권한 검증'을 통과시키기 위해, DB에 1번 매칭 및 1번 채팅방 정보를 강제 주입합니다.</li>
 *   <li>현재 DB에 등록된 모든 유저를 1번 채팅방의 참여자(REQUESTER)로 강제 등록합니다.</li>
 * </ul>
 *
 * <p>이 파일이 활성화되어 있어야 프론트엔드 테스트 페이지에서 방 번호 1번으로 즉시 실시간 채팅을 진행할 수 있습니다.
 * 나중에 실시간 1:1 매칭 기능 개발이 완료되어 매칭에 의해 채팅방이 실제 생성될 때는 이 파일을 안전하게 삭제하면 됩니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketTestFixtureRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[TestFixture] pg_constraint 에서 제약조건 정의 조회 시도...");
            List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                    "SELECT conname, pg_get_constraintdef(oid) as def FROM pg_constraint WHERE conname = 'match_participants_role_check'"
            );
            if (!constraints.isEmpty()) {
                log.info("[TestFixture] 제약조건 'match_participants_role_check' 정의: {}", constraints.get(0).get("def"));
            } else {
                log.info("[TestFixture] 제약조건 'match_participants_role_check'를 찾지 못함");
            }

            log.info("[TestFixture] 채팅 테스트용 데이터 자동 구축 시작...");

            // 1. 테스트용 로컬 유저 2명 강제 삽입 (소셜 계정이 아니더라도 로그인을 위한 유저)
            insertDummyUser("usera@test.com", "테스트A");
            insertDummyUser("userb@test.com", "테스트B");

            // 2. 현재 DB에 존재하는 모든 유저의 ID를 가져옴
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, nickname FROM users");
            if (users.size() < 2) {
                log.warn("[TestFixture] 유저 수가 부족합니다.");
                return;
            }

            // 3. matches 테이블 생성을 위한 match_requests 더미 2건 강제 삽입 (FK 제약 조건 만족용)
            // request_1_id, request_2_id가 NOT NULL 이므로 먼저 삽입되어야 함
            UUID userAId = (UUID) users.get(0).get("id");
            UUID userBId = (UUID) users.get(1).get("id");

            jdbcTemplate.execute(
                    "INSERT INTO match_requests (id, user_id, food_category, meal_at, region_code, region_name, location_name, location, status, reject_count, created_at, updated_at) " +
                    "VALUES (1, '" + userAId + "', 'KOREAN', NOW(), '11680', '서울특별시 강남구', '강남역 11번 출구', ST_GeomFromText('POINT(127.0473 37.5172)', 4326)::geography, 'MATCHED', 0, NOW(), NOW()) " +
                    "ON CONFLICT (id) DO NOTHING"
            );

            jdbcTemplate.execute(
                    "INSERT INTO match_requests (id, user_id, food_category, meal_at, region_code, region_name, location_name, location, status, reject_count, created_at, updated_at) " +
                    "VALUES (2, '" + userBId + "', 'KOREAN', NOW(), '11680', '서울특별시 강남구', '강남역 11번 출구', ST_GeomFromText('POINT(127.0473 37.5172)', 4326)::geography, 'MATCHED', 0, NOW(), NOW()) " +
                    "ON CONFLICT (id) DO NOTHING"
            );

            // 4. 테스트용 1번 매칭 생성
            jdbcTemplate.execute(
                    "INSERT INTO matches (id, request_1_id, request_2_id, status, matched_at, created_at, updated_at) " +
                    "VALUES (1, 1, 2, 'MATCHED', NOW(), NOW(), NOW()) " +
                    "ON CONFLICT (id) DO NOTHING"
            );

            // 5. 테스트용 1번 채팅방 생성
            jdbcTemplate.execute(
                    "INSERT INTO chat_rooms (id, match_id, status, created_at) " +
                    "VALUES (1, 1, 'ACTIVE', NOW()) " +
                    "ON CONFLICT (id) DO NOTHING"
            );

            // 5. 모든 유저를 1번 방의 참여자로 등록
            for (Map<String, Object> userMap : users) {
                Object rawId = userMap.get("id");
                UUID userId = (rawId instanceof UUID) ? (UUID) rawId : UUID.fromString(rawId.toString());
                String nickname = (String) userMap.get("nickname");

                jdbcTemplate.update(
                        "INSERT INTO match_participants (match_id, user_id, role, joined_at) " +
                        "VALUES (1, ?, 'REQUESTER', NOW()) " +
                        "ON CONFLICT (match_id, user_id) DO NOTHING",
                        userId
                );
                log.info("[TestFixture] 유저 {} ({}) 1번 방 참여 등록", nickname, userId);
            }

            log.info("[TestFixture] 채팅 테스트용 데이터 구축 성공! (채팅방 ID = 1)");
        } catch (Exception e) {
            log.error("[TestFixture] 채팅 테스트 데이터 구축 중 심각한 예외 발생: {}", e.getMessage(), e);
        }
    }

    private void insertDummyUser(String email, String nickname) {
        try {
            // 이메일 중복 체크 후 삽입
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
            if (count == null || count == 0) {
                UUID newId = UUID.randomUUID();
                // 패스워드는 임의의 더미값 삽입
                jdbcTemplate.update(
                        "INSERT INTO users (id, email, password_hash, nickname, provider, role, status, personality_onboarding_status, created_at, updated_at) " +
                        "VALUES (?, ?, 'dummy_hash', ?, 'LOCAL', 'USER', 'ACTIVE', 'NOT_STARTED', NOW(), NOW())",
                        newId, email, nickname
                );
                log.info("[TestFixture] 더미 계정 생성 완료: {} ({})", email, newId);
            }
        } catch (Exception e) {
            log.warn("[TestFixture] 더미 계정 생성 실패 (이미 존재할 수 있음): {}", e.getMessage());
        }
    }
}

