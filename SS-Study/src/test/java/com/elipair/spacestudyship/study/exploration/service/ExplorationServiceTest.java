package com.elipair.spacestudyship.study.exploration.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import com.elipair.spacestudyship.study.exploration.repository.ExplorationNodeRepository;
import com.elipair.spacestudyship.study.exploration.repository.UserExplorationRepository;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
}
