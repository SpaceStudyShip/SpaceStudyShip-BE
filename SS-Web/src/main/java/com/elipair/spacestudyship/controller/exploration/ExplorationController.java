package com.elipair.spacestudyship.controller.exploration;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.PlanetUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionUnlockResponse;
import com.elipair.spacestudyship.study.exploration.service.ExplorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Exploration", description = "우주 탐험(행성/지역 해금) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/explorations")
public class ExplorationController {

    private final ExplorationService explorationService;

    @Operation(summary = "행성 목록 조회",
            description = "전체 행성 목록과 유저의 해금/클리어 상태, 진행도를 반환합니다. 정렬: sortOrder 오름차순.")
    @GetMapping("/planets")
    public ResponseEntity<List<PlanetResponse>> getPlanets(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(explorationService.getPlanets(loginMember.memberId()));
    }

    @Operation(summary = "행성 하위 지역 목록 조회",
            description = "특정 행성의 하위 지역과 유저 해금 상태를 반환합니다. 행성이 없으면 404 PLANET_NOT_FOUND.")
    @GetMapping("/planets/{planetId}/regions")
    public ResponseEntity<List<RegionResponse>> getRegions(
            @AuthMember LoginMember loginMember,
            @PathVariable String planetId) {
        return ResponseEntity.ok(explorationService.getRegions(loginMember.memberId(), planetId));
    }

    @Operation(summary = "지역 해금",
            description = """
                연료를 소비하여 지역을 해금합니다(해금=클리어). 잔량 확인+차감+해금을 원자적으로 처리합니다.
                상위 행성의 모든 지역이 해금되면 planetCleared=true.

                에러: 400 INSUFFICIENT_FUEL(requiredFuel/currentFuel 동봉) / ALREADY_UNLOCKED / PLANET_LOCKED, 404 REGION_NOT_FOUND
                """)
    @PostMapping("/regions/{regionId}/unlock")
    public ResponseEntity<RegionUnlockResponse> unlockRegion(
            @AuthMember LoginMember loginMember,
            @PathVariable String regionId) {
        return ResponseEntity.ok(explorationService.unlockRegion(loginMember.memberId(), regionId));
    }

    @Operation(summary = "행성 해금",
            description = """
                연료를 소비하여 행성을 해금합니다. 선행 행성을 클리어해야 해금할 수 있습니다.

                에러: 400 INSUFFICIENT_FUEL(requiredFuel/currentFuel 동봉) / ALREADY_UNLOCKED / PREREQUISITE_NOT_CLEARED, 404 PLANET_NOT_FOUND
                """)
    @PostMapping("/planets/{planetId}/unlock")
    public ResponseEntity<PlanetUnlockResponse> unlockPlanet(
            @AuthMember LoginMember loginMember,
            @PathVariable String planetId) {
        return ResponseEntity.ok(explorationService.unlockPlanet(loginMember.memberId(), planetId));
    }
}
