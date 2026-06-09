package com.elipair.spacestudyship.study.todo.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TodoCategoryTest {

    @Test
    @DisplayName("create: 정적 팩토리로 카테고리 생성")
    void create() {
        TodoCategory category = TodoCategory.create(
                "cat-1", 1L, "수학", "math_icon", 0.3, 0.5);

        assertThat(category.getId()).isEqualTo("cat-1");
        assertThat(category.getUserId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("수학");
        assertThat(category.getIconId()).isEqualTo("math_icon");
        assertThat(category.getPositionX()).isEqualTo(0.3);
        assertThat(category.getPositionY()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("updateName: 이름 변경")
    void updateName() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", null, null, null);
        category.updateName("심화수학");
        assertThat(category.getName()).isEqualTo("심화수학");
    }

    @Test
    @DisplayName("updateIconId: 아이콘 변경")
    void updateIconId() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", "math_icon", null, null);
        category.updateIconId("new_icon");
        assertThat(category.getIconId()).isEqualTo("new_icon");
    }

    @Test
    @DisplayName("updatePositionX/Y: 위치 변경")
    void updatePosition() {
        TodoCategory category = TodoCategory.create("cat-1", 1L, "수학", null, 0.3, 0.5);
        category.updatePositionX(0.7);
        category.updatePositionY(0.2);
        assertThat(category.getPositionX()).isEqualTo(0.7);
        assertThat(category.getPositionY()).isEqualTo(0.2);
    }
}
