package com.elipair.spacestudyship.study.exploration.entity;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.constant.NodeTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exploration_nodes")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorationNode {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 50)
    private String name;

    @Convert(converter = NodeTypeConverter.class)
    @Column(name = "node_type", nullable = false, length = 10)
    private NodeType nodeType;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false, length = 30)
    private String icon;

    @Column(name = "parent_id", length = 50)
    private String parentId;

    @Column(name = "prerequisite_node_id", length = 50)
    private String prerequisiteNodeId;

    @Column(name = "required_fuel", nullable = false)
    private int requiredFuel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "map_x", nullable = false)
    private double mapX;

    @Column(name = "map_y", nullable = false)
    private double mapY;
}
