package com.elipair.spacestudyship.study.exploration.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import com.elipair.spacestudyship.study.exploration.repository.ExplorationNodeRepository;
import com.elipair.spacestudyship.study.exploration.repository.UserExplorationRepository;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplorationService {

    private final ExplorationNodeRepository nodeRepository;
    private final UserExplorationRepository userExplorationRepository;
    private final FuelService fuelService;

    public List<PlanetResponse> getPlanets(Long userId) {
        List<ExplorationNode> planets = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET);
        List<ExplorationNode> regions = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.REGION);
        Map<String, UserExploration> progress = progressMap(userId);
        Set<String> unlocked = progress.keySet();

        Map<String, Long> totalByParent = regions.stream()
                .collect(Collectors.groupingBy(ExplorationNode::getParentId, Collectors.counting()));
        Map<String, Long> clearedByParent = regions.stream()
                .filter(r -> unlocked.contains(r.getId()))
                .collect(Collectors.groupingBy(ExplorationNode::getParentId, Collectors.counting()));

        return planets.stream().map(p -> {
            int total = totalByParent.getOrDefault(p.getId(), 0L).intValue();
            int cleared = clearedByParent.getOrDefault(p.getId(), 0L).intValue();
            boolean isUnlocked = p.getRequiredFuel() == 0 || unlocked.contains(p.getId());
            boolean isCleared = total > 0 && cleared == total;
            double ratio = total == 0 ? 0.0 : (double) cleared / total;
            LocalDateTime unlockedAt = progress.containsKey(p.getId())
                    ? progress.get(p.getId()).getUnlockedAt() : null;
            return PlanetResponse.of(p, isUnlocked, isCleared, cleared, total, ratio, unlockedAt);
        }).toList();
    }

    public List<RegionResponse> getRegions(Long userId, String planetId) {
        nodeRepository.findById(planetId)
                .filter(n -> n.getNodeType() == NodeType.PLANET)
                .orElseThrow(() -> new CustomException(ErrorCode.PLANET_NOT_FOUND));

        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc(planetId);
        Map<String, UserExploration> progress = progressMap(userId);

        return regions.stream().map(r -> {
            UserExploration pr = progress.get(r.getId());
            boolean isUnlocked = r.getRequiredFuel() == 0 || pr != null;
            LocalDateTime unlockedAt = pr == null ? null : pr.getUnlockedAt();
            return RegionResponse.of(r, isUnlocked, isUnlocked, unlockedAt);
        }).toList();
    }

    private Map<String, UserExploration> progressMap(Long userId) {
        return userExplorationRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserExploration::getNodeId, Function.identity()));
    }

    private boolean isPlanetCleared(Long userId, String planetId) {
        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc(planetId);
        if (regions.isEmpty()) {
            return false;
        }
        Set<String> unlocked = userExplorationRepository.findByUserId(userId).stream()
                .map(UserExploration::getNodeId).collect(Collectors.toSet());
        return regions.stream().allMatch(r -> unlocked.contains(r.getId()));
    }
}
