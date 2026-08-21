package org.example.project2.domain.restaurant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.project2.global.entity.BaseEntity;
import org.hibernate.annotations.Check;
import org.locationtech.jts.geom.Point;

@Table(name = "restaurants")
@Entity
@Check(constraints = "review_count >= 0")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class Restaurant extends BaseEntity {
    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(length = 50)
    private String phone;

    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;
}
