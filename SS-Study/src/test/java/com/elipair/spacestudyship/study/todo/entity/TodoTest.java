package com.elipair.spacestudyship.study.todo.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TodoTest {

    @Test
    @DisplayName("create: 정적 팩토리로 Todo 생성 — null 배열은 빈 배열로 정규화")
    void create_nullArraysNormalizedToEmpty() {
        Todo todo = Todo.create("t1", 1L, "수학 문제", null, null, 60);

        assertThat(todo.getId()).isEqualTo("t1");
        assertThat(todo.getUserId()).isEqualTo(1L);
        assertThat(todo.getTitle()).isEqualTo("수학 문제");
        assertThat(todo.getScheduledDates()).isEmpty();
        assertThat(todo.getCompletedDates()).isEmpty();
        assertThat(todo.getCategoryIds()).isEmpty();
        assertThat(todo.getEstimatedMinutes()).isEqualTo(60);
        assertThat(todo.getActualMinutes()).isNull();
    }

    @Test
    @DisplayName("create: 값이 있으면 그대로 사용")
    void create_withValues() {
        Todo todo = Todo.create(
                "t1", 1L, "수학",
                List.of("2026-04-16"),
                List.of("cat-1"),
                90);

        assertThat(todo.getScheduledDates()).containsExactly("2026-04-16");
        assertThat(todo.getCategoryIds()).containsExactly("cat-1");
    }

    @Test
    @DisplayName("updateTitle / updateScheduledDates / updateCompletedDates / updateCategoryIds / updateEstimatedMinutes / updateActualMinutes")
    void updaters() {
        Todo todo = Todo.create("t1", 1L, "원본", null, null, null);

        todo.updateTitle("새 제목");
        todo.updateScheduledDates(List.of("2026-05-01"));
        todo.updateCompletedDates(List.of("2026-05-01"));
        todo.updateCategoryIds(List.of("c1", "c2"));
        todo.updateEstimatedMinutes(120);
        todo.updateActualMinutes(45);

        assertThat(todo.getTitle()).isEqualTo("새 제목");
        assertThat(todo.getScheduledDates()).containsExactly("2026-05-01");
        assertThat(todo.getCompletedDates()).containsExactly("2026-05-01");
        assertThat(todo.getCategoryIds()).containsExactly("c1", "c2");
        assertThat(todo.getEstimatedMinutes()).isEqualTo(120);
        assertThat(todo.getActualMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("removeCategoryId: 해당 ID만 제거 (immutable copy)")
    void removeCategoryId() {
        Todo todo = Todo.create("t1", 1L, "수학", null, List.of("c1", "c2", "c3"), null);

        todo.removeCategoryId("c2");

        assertThat(todo.getCategoryIds()).containsExactly("c1", "c3");
    }

    @Test
    @DisplayName("removeCategoryId: 존재하지 않는 ID면 무변화")
    void removeCategoryId_notExist() {
        Todo todo = Todo.create("t1", 1L, "수학", null, List.of("c1"), null);

        todo.removeCategoryId("c-missing");

        assertThat(todo.getCategoryIds()).containsExactly("c1");
    }
}
