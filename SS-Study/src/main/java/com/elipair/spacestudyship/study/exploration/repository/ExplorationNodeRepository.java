package com.elipair.spacestudyship.study.exploration.repository;

import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExplorationNodeRepository extends JpaRepository<ExplorationNode, String> {

    List<ExplorationNode> findByNodeTypeOrderBySortOrderAsc(NodeType nodeType);

    List<ExplorationNode> findByParentIdOrderBySortOrderAsc(String parentId);
}
