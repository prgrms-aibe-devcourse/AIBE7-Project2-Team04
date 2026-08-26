package org.example.project2.domain.region.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @Column(name = "region_code", length = 5)
    private String regionCode;

    @Column(name = "city_do", nullable = false, length = 50)
    private String cityDo;

    @Column(name = "sigungu", nullable = false, length = 50)
    private String sigungu;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "center_location", nullable = false, columnDefinition = "geography(Point, 4326)")
    private Point centerLocation;

    @Builder
    public Region(String regionCode, String cityDo, String sigungu, String fullName, Point centerLocation) {
        this.regionCode = regionCode;
        this.cityDo = cityDo;
        this.sigungu = sigungu;
        this.fullName = fullName;
        this.centerLocation = centerLocation;
    }
}
