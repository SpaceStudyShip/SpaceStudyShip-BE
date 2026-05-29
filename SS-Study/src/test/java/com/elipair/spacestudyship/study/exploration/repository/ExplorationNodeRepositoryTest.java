package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class ExplorationNodeRepositoryTest {

    @Autowired
    ExplorationNodeRepository nodeRepository;

    private ExplorationNode planet(String id, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.PLANET)
                .depth(2).icon(id).requiredFuel(0).sortOrder(sort)
                .description("").mapX(0).mapY(0).build();
    }

    private ExplorationNode region(String id, String parent, int sort) {
        return ExplorationNode.builder().id(id).name(id).nodeType(NodeType.REGION)
                .depth(3).icon(id).parentId(parent).requiredFuel(1).sortOrder(sort)
                .description("").mapX(0).mapY(0).build();
    }

    @Test
    @DisplayName("findByNodeTypeOrderBySortOrderAsc: 타입 필터 + 정렬")
    void findByNodeType_sorted() {
        nodeRepository.saveAll(List.of(planet("b", 1), planet("a", 0)));
        nodeRepository.saveAll(List.of(region("r1", "a", 0)));

        List<ExplorationNode> planets = nodeRepository.findByNodeTypeOrderBySortOrderAsc(NodeType.PLANET);

        assertThat(planets).extracting(ExplorationNode::getId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("findByParentIdOrderBySortOrderAsc: 부모별 정렬 조회")
    void findByParent_sorted() {
        nodeRepository.save(planet("a", 0));
        nodeRepository.saveAll(List.of(region("r2", "a", 1), region("r1", "a", 0)));

        List<ExplorationNode> regions = nodeRepository.findByParentIdOrderBySortOrderAsc("a");

        assertThat(regions).extracting(ExplorationNode::getId).containsExactly("r1", "r2");
    }
}
