package org.example.project2.domain.region.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.project2.domain.region.repository.RegionRepository;
import org.example.project2.global.common.CommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Region", description = "행정구역 및 지도 위치 API")
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;

    public record RegionResponse(
            String regionCode,
            String regionName,
            double centerLatitude,
            double centerLongitude
    ) {}

    @Operation(summary = "지원하는 구 단위 행정구역과 지도 대표 좌표 조회")
    @GetMapping
    public ResponseEntity<CommonResponse<List<RegionResponse>>> getRegions(
            @RequestParam(name = "level", defaultValue = "GU") String level,
            @RequestParam(name = "parentCode", required = false) String parentCode
    ) {
        List<org.example.project2.domain.region.entity.Region> regions;
        
        if (parentCode != null && !parentCode.isBlank()) {
            regions = regionRepository.findByRegionCodeStartingWith(parentCode);
        } else {
            regions = regionRepository.findAll();
        }

        List<RegionResponse> responseList = regions.stream()
                .map(r -> new RegionResponse(
                        r.getRegionCode(),
                        r.getFullName(),
                        r.getCenterLocation().getY(), // 위도 (y)
                        r.getCenterLocation().getX()  // 경도 (x)
                ))
                .toList();

        return ResponseEntity.ok(CommonResponse.success(responseList));
    }
}
