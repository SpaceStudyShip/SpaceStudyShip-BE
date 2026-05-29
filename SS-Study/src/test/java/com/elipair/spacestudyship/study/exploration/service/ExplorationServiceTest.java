package com.elipair.spacestudyship.study.exploration.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.InsufficientFuelException;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import com.elipair.spacestudyship.study.exploration.repository.ExplorationNodeRepository;
import com.elipair.spacestudyship.study.exploration.repository.UserExplorationRepository;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExplorationServiceTest {

    @Mock ExplorationNodeRepository nodeRepository;
    @Mock UserExplorationRepository userExplorationRepository;
    @Mock FuelService fuelService;
    @InjectMocks ExplorationService service;

    private ExplorationNode planet(String id, int requiredFuel, String prereq, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.PLANET).depth(2)
                .icon(id).parentId(null).prerequisiteNodeId(prereq)
                .requiredFuel(requiredFuel).sortOrder(sort).description("").mapX(0).mapY(0).build();
    }

    private ExplorationNode region(String id, String parent, int requiredFuel, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.REGION).depth(3)
                .icon(id).parentId(parent).prerequisiteNodeId(null)
                .requiredFuel(requiredFuel).sortOrder(sort).description("").mapX(0).mapY(0).build();
    }

    @Test
    @DisplayName("getPlanets: earth는 requiredFuel=0이라 암묵 해금, 진행도 파생")
    void getPlanets_derivesUnlockAndProgress() {
        given(nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET))
                .willReturn(List.of(planet("earth", 0, null, 0), planet("mercury", 3, "earth", 1)));
        given(nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.REGION))
                .willReturn(List.of(region("korea", "earth", 0, 0),
                        region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));

        List<PlanetResponse> result = service.getPlanets(1L);

        PlanetResponse earth = result.get(0);
        assertThat(earth.id()).isEqualTo("earth");
        assertThat(earth.isUnlocked()).isTrue();
        assertThat(earth.isCleared()).isFalse();
        assertThat(earth.progress().clearedChildren()).isEqualTo(1);
        assertThat(earth.progress().totalChildren()).isEqualTo(2);
        assertThat(earth.progress().progressRatio()).isEqualTo(0.5);

        PlanetResponse mercury = result.get(1);
        assertThat(mercury.isUnlocked()).isFalse();
        assertThat(mercury.prerequisiteId()).isEqualTo("earth");
    }

    @Test
    @DisplayName("getRegions: 행성 없으면 PLANET_NOT_FOUND")
    void getRegions_planetNotFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRegions(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("getRegions: 해금된 지역 isUnlocked/isCleared=true, korea(연료0) 암묵 해금")
    void getRegions_mapsUnlock() {
        given(nodeRepository.findById("earth")).willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0),
                        region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L)).willReturn(List.of());

        List<RegionResponse> result = service.getRegions(1L, "earth");

        assertThat(result).extracting(RegionResponse::id).containsExactly("korea", "japan");
        assertThat(result.get(0).isUnlocked()).isTrue();   // korea requiredFuel=0 → 암묵 해금
        assertThat(result.get(0).isCleared()).isTrue();
        assertThat(result.get(1).isUnlocked()).isFalse();  // japan 미해금
    }

    private FuelResponse fuel(int currentFuel) {
        return new FuelResponse(currentFuel, 0, 0, 0, null);
    }

    private FuelTransactionResponse tx(int amount, int balanceAfter) {
        return new FuelTransactionResponse(
                "tx", "consume", amount, "EXPLORATION_UNLOCK", "ref", balanceAfter, null);
    }

    @Test
    @DisplayName("unlockRegion: 정상 해금 — 잔량충분 + 차감 + 저장 + 마지막 지역이면 planetCleared=true")
    void unlockRegion_success_lastRegionClearsPlanet() {
        given(nodeRepository.findById("japan"))
                .willReturn(Optional.of(region("japan", "earth", 1, 1)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "japan")).willReturn(false);
        given(fuelService.getFuel(1L)).willReturn(fuel(250));
        given(fuelService.consume(eq(1L), eq(1), eq(FuelReason.EXPLORATION_UNLOCK), eq("japan"), anyString()))
                .willReturn(tx(1, 249));
        given(userExplorationRepository.save(any(UserExploration.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0), region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true),
                        UserExploration.unlock(1L, "japan", true)));

        var result = service.unlockRegion(1L, "japan");

        assertThat(result.region().id()).isEqualTo("japan");
        assertThat(result.region().isCleared()).isTrue();
        assertThat(result.fuelConsumed()).isEqualTo(1);
        assertThat(result.currentFuel()).isEqualTo(249);
        assertThat(result.planetCleared()).isTrue();

        ArgumentCaptor<UserExploration> captor = ArgumentCaptor.forClass(UserExploration.class);
        verify(userExplorationRepository).save(captor.capture());
        assertThat(captor.getValue().getNodeId()).isEqualTo("japan");
        assertThat(captor.getValue().isCleared()).isTrue();
    }

    @Test
    @DisplayName("unlockRegion: 잔량 부족 → InsufficientFuelException + consume 미호출")
    void unlockRegion_insufficientFuel() {
        given(nodeRepository.findById("usa"))
                .willReturn(Optional.of(region("usa", "earth", 3, 8)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "usa")).willReturn(false);
        given(fuelService.getFuel(1L)).willReturn(fuel(1));

        assertThatThrownBy(() -> service.unlockRegion(1L, "usa"))
                .isInstanceOf(InsufficientFuelException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 부모 행성 미해금 → PLANET_LOCKED")
    void unlockRegion_parentLocked() {
        given(nodeRepository.findById("mars_olympus"))
                .willReturn(Optional.of(region("mars_olympus", "mars", 3, 0)));
        given(nodeRepository.findById("mars"))
                .willReturn(Optional.of(planet("mars", 10, "venus", 3)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mars")).willReturn(false);

        assertThatThrownBy(() -> service.unlockRegion(1L, "mars_olympus"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 이미 해금 → ALREADY_UNLOCKED")
    void unlockRegion_alreadyUnlocked() {
        given(nodeRepository.findById("japan"))
                .willReturn(Optional.of(region("japan", "earth", 1, 1)));
        given(nodeRepository.findById("earth"))
                .willReturn(Optional.of(planet("earth", 0, null, 0)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "japan")).willReturn(true);

        assertThatThrownBy(() -> service.unlockRegion(1L, "japan"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockRegion: 없는 지역 → REGION_NOT_FOUND")
    void unlockRegion_notFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlockRegion(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("unlockPlanet: 선행 행성 클리어 시 정상 해금")
    void unlockPlanet_success() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));
        given(fuelService.getFuel(1L)).willReturn(fuel(100));
        given(fuelService.consume(eq(1L), eq(3), eq(FuelReason.EXPLORATION_UNLOCK), eq("mercury"), anyString()))
                .willReturn(tx(3, 97));
        given(userExplorationRepository.save(any(UserExploration.class)))
                .willAnswer(inv -> inv.getArgument(0));

        var result = service.unlockPlanet(1L, "mercury");

        assertThat(result.planet().id()).isEqualTo("mercury");
        assertThat(result.planet().isCleared()).isFalse();
        assertThat(result.fuelConsumed()).isEqualTo(3);
        assertThat(result.currentFuel()).isEqualTo(97);
    }

    @Test
    @DisplayName("unlockPlanet: 선행 미클리어 → PREREQUISITE_NOT_CLEARED + consume 미호출")
    void unlockPlanet_prerequisiteNotCleared() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0), region("japan", "earth", 1, 1)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true))); // 1/2만

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 잔량 부족 → InsufficientFuelException + consume 미호출")
    void unlockPlanet_insufficientFuel() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(false);
        given(nodeRepository.findByParentIdOrderBySortOrderAsc("earth"))
                .willReturn(List.of(region("korea", "earth", 0, 0)));
        given(userExplorationRepository.findByUserId(1L))
                .willReturn(List.of(UserExploration.unlock(1L, "korea", true)));
        given(fuelService.getFuel(1L)).willReturn(fuel(1));

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(InsufficientFuelException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 이미 해금 → ALREADY_UNLOCKED")
    void unlockPlanet_alreadyUnlocked() {
        given(nodeRepository.findById("mercury"))
                .willReturn(Optional.of(planet("mercury", 3, "earth", 1)));
        given(userExplorationRepository.existsByUserIdAndNodeId(1L, "mercury")).willReturn(true);

        assertThatThrownBy(() -> service.unlockPlanet(1L, "mercury"))
                .isInstanceOf(CustomException.class);
        verify(fuelService, never()).consume(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("unlockPlanet: 없는 행성 → PLANET_NOT_FOUND")
    void unlockPlanet_notFound() {
        given(nodeRepository.findById("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlockPlanet(1L, "nope"))
                .isInstanceOf(CustomException.class);
    }
}
