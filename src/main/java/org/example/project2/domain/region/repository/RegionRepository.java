package org.example.project2.domain.region.repository;

import org.example.project2.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {
    List<Region> findByRegionCodeStartingWith(String parentCode);
}
