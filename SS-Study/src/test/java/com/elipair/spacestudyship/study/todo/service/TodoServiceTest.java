package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock TodoRepository todoRepository;
    @Mock TodoCategoryRepository categoryRepository;
    @Mock EntityManager entityManager;
    @InjectMocks TodoService todoService;

    @Test
    @DisplayName("findAll: 필터 없음 → findByUserIdOrderByCreatedAtDesc 호출")
    void findAll_noFilters() {
        when(todoRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", null, null, null)));

        var result = todoService.findAll(1L, null, null);

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("findAll: date 필터만")
    void findAll_dateOnly() {
        when(todoRepository.findByUserIdAndScheduledDate(1L, "\"2026-04-16\""))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", List.of("2026-04-16"), null, null)));

        var result = todoService.findAll(1L, "2026-04-16", null);

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdAndScheduledDate(1L, "\"2026-04-16\"");
    }

    @Test
    @DisplayName("findAll: categoryId 필터만")
    void findAll_categoryOnly() {
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(Todo.create("t1", 1L, "X", null, List.of("c1"), null)));

        var result = todoService.findAll(1L, null, "c1");

        assertThat(result).hasSize(1);
        verify(todoRepository).findByUserIdAndCategoryId(1L, "\"c1\"");
    }

    @Test
    @DisplayName("findAll: date + categoryId — 두 쿼리 결과의 교집합")
    void findAll_dateAndCategory() {
        Todo a = Todo.create("a", 1L, "AB", List.of("2026-04-16"), List.of("c1"), null);
        Todo b = Todo.create("b", 1L, "B만", List.of("2026-04-16"), List.of("c2"), null);
        when(todoRepository.findByUserIdAndScheduledDate(1L, "\"2026-04-16\""))
                .thenReturn(List.of(a, b));
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(a));

        var result = todoService.findAll(1L, "2026-04-16", "c1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("a");
    }

    @Test
    @DisplayName("create: id 미지정 → 서버가 UUID 생성, 카테고리 검증 통과")
    void create_serverGeneratedId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                null, "수학", java.util.List.of(), 60, java.util.List.of("2026-04-16"));
        when(todoRepository.existsById(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(todoRepository.save(org.mockito.ArgumentMatchers.any(Todo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = todoService.create(1L, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.title()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 동일 ID 존재 → TODO_ALREADY_EXISTS")
    void create_duplicateId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t1", "수학", java.util.List.of(), null, java.util.List.of());
        when(todoRepository.existsById("t1")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.create(1L, request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("create: categoryIds에 존재하지 않는 ID → CATEGORY_NOT_FOUND")
    void create_invalidCategoryId() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t1", "수학", java.util.List.of("missing-cat"), null, java.util.List.of());
        when(todoRepository.existsById("t1")).thenReturn(false);
        when(categoryRepository.countByIdInAndUserId(java.util.List.of("missing-cat"), 1L))
                .thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.create(1L, request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("update: title만 변경, 나머지 null → 기존 유지")
    void update_titleOnly() {
        Todo existing = Todo.create("t1", 1L, "원본", java.util.List.of("2026-04-16"),
                java.util.List.of("c1"), 60);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "새 제목", null, null, null, null, null);

        var response = todoService.update(1L, "t1", request);

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.scheduledDates()).containsExactly("2026-04-16");
        assertThat(response.categoryIds()).containsExactly("c1");
        assertThat(response.estimatedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("update: 빈 배열은 명시적 모두 제거")
    void update_emptyArrayClears() {
        Todo existing = Todo.create("t1", 1L, "X", java.util.List.of("2026-04-16"),
                java.util.List.of("c1"), null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                null, java.util.List.of(), null, null, null, null);

        var response = todoService.update(1L, "t1", request);

        assertThat(response.scheduledDates()).isEmpty();
        assertThat(response.categoryIds()).containsExactly("c1");
    }

    @Test
    @DisplayName("update: 존재하지 않는 todoId → TODO_NOT_FOUND")
    void update_notFound() {
        when(todoRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(java.util.Optional.empty());

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "X", null, null, null, null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.update(1L, "missing", request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    @DisplayName("update: categoryIds 변경 시 검증")
    void update_categoryIdsValidated() {
        Todo existing = Todo.create("t1", 1L, "X", null, null, null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));
        when(categoryRepository.countByIdInAndUserId(java.util.List.of("missing"), 1L))
                .thenReturn(0L);

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                null, null, null, java.util.List.of("missing"), null, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.update(1L, "t1", request))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("delete: 본인 Todo 삭제 성공 (deleteByIdAndUserId atomic)")
    void delete_success() {
        when(todoRepository.deleteByIdAndUserId("t1", 1L)).thenReturn(1L);

        todoService.delete(1L, "t1");

        verify(todoRepository).deleteByIdAndUserId("t1", 1L);
    }

    @Test
    @DisplayName("delete: deleted count 0이면 TODO_NOT_FOUND")
    void delete_notFound() {
        when(todoRepository.deleteByIdAndUserId("missing", 1L)).thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> todoService.delete(1L, "missing"))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    @DisplayName("create: save 후 EntityManager.flush() 호출 — createdAt/updatedAt 보장")
    void create_flushesAfterSave() {
        var request = new com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest(
                "t-new", "수학", java.util.List.of(), null, java.util.List.of("2026-05-25"));
        when(todoRepository.existsById("t-new")).thenReturn(false);
        when(todoRepository.save(org.mockito.ArgumentMatchers.any(Todo.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        todoService.create(1L, request);

        verify(entityManager).flush();
    }

    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 갱신 보장")
    void update_flushesAfterMutation() {
        Todo existing = Todo.create("t1", 1L, "원본", null, null, null);
        when(todoRepository.findByIdAndUserId("t1", 1L))
                .thenReturn(java.util.Optional.of(existing));

        var request = new com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest(
                "새 제목", null, null, null, null, null);

        todoService.update(1L, "t1", request);

        verify(entityManager).flush();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: 정상 흐름 — repository 호출 및 로그")
    void addActualMinutes_success() {
        org.mockito.BDDMockito.given(todoRepository.addActualMinutes(1L, "t-1", 30))
                .willReturn(1);

        todoService.addActualMinutes(1L, "t-1", 30);

        org.mockito.Mockito.verify(todoRepository).addActualMinutes(1L, "t-1", 30);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: minutes <= 0 → INVALID_INPUT_VALUE")
    void addActualMinutes_nonPositive_throws() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> todoService.addActualMinutes(1L, "t-1", 0))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: 영향 row 0 → TODO_NOT_FOUND")
    void addActualMinutes_notFound_throws() {
        org.mockito.BDDMockito.given(todoRepository.addActualMinutes(1L, "nope", 30))
                .willReturn(0);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> todoService.addActualMinutes(1L, "nope", 30))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }
}
