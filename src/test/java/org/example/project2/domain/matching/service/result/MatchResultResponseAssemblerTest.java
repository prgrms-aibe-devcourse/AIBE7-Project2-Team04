package org.example.project2.domain.matching.service.result;

import org.example.project2.domain.chat.entity.ChatRoom;
import org.example.project2.domain.matching.entity.Match;
import org.example.project2.domain.matching.entity.MatchProposal;
import org.example.project2.domain.matching.entity.MatchRequest;
import org.example.project2.domain.personality.repository.UserPersonalityProfileRepository;
import org.example.project2.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MatchResultResponseAssemblerTest {
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    private UserPersonalityProfileRepository personalityProfileRepository;

    @Test
    void returnsViewerSpecificDesiredLocationsInLatitudeLongitudeOrder() {
        User user1 = user("첫번째", "first@example.com");
        User user2 = user("두번째", "second@example.com");
        MatchRequest request1 = request(user1, 1L, "강남역", 127.0276, 37.4979);
        MatchRequest request2 = request(user2, 2L, "역삼역", 127.0365, 37.5007);
        MatchProposal proposal = MatchProposal.of(request1, request2, null, Instant.now().plusSeconds(15));
        Match match = Match.of(request1, request2, Instant.now());
        ReflectionTestUtils.setField(match, "id", 10L);
        ChatRoom chatRoom = ChatRoom.builder().match(match).build();
        ReflectionTestUtils.setField(chatRoom, "id", 20L);
        MatchResultResponseAssembler assembler = new MatchResultResponseAssembler(personalityProfileRepository);

        MatchResultResponseAssembler.MatchResultViews views = assembler.assemble(proposal, match, chatRoom);

        var user1Locations = views.responseFor(user1.getId()).desiredLocations();
        assertThat(user1Locations.mine().locationName()).isEqualTo("강남역");
        assertThat(user1Locations.mine().latitude()).isEqualTo(37.4979);
        assertThat(user1Locations.mine().longitude()).isEqualTo(127.0276);
        assertThat(user1Locations.mine().foodCategory()).isEqualTo("KOREAN");
        assertThat(user1Locations.partner().locationName()).isEqualTo("역삼역");

        var user2Locations = views.responseFor(user2.getId()).desiredLocations();
        assertThat(user2Locations.mine().locationName()).isEqualTo("역삼역");
        assertThat(user2Locations.partner().locationName()).isEqualTo("강남역");
    }

    private User user(String nickname, String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("argon2-hash")
                .nickname(nickname)
                .build();
    }

    private MatchRequest request(User user, long id, String locationName, double longitude, double latitude) {
        var point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        MatchRequest request = MatchRequest.builder()
                .user(user)
                .foodCategory("KOREAN")
                .mealAt(Instant.now().plusSeconds(3600))
                .regionCode("11680")
                .regionName("서울특별시 강남구")
                .locationName(locationName)
                .location(point)
                .searchRadius(3000)
                .desiredPersonalityTags(Set.of())
                .build();
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}
