package com.elipair.spacestudyship.study.exploration.entity;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplorationNodeTest {

    @Test
    @DisplayName("planet 빌더: 필드 매핑")
    void buildsPlanet() {
        ExplorationNode node = ExplorationNode.builder()
                .id("earth").name("지구").nodeType(NodeType.PLANET).depth(2)
                .icon("earth").parentId(null).prerequisiteNodeId(null)
                .requiredFuel(0).sortOrder(0).description("시작점")
                .mapX(0.5).mapY(0.08).build();

        assertThat(node.getId()).isEqualTo("earth");
        assertThat(node.getNodeType()).isEqualTo(NodeType.PLANET);
        assertThat(node.getRequiredFuel()).isZero();
        assertThat(node.getParentId()).isNull();
    }
}
