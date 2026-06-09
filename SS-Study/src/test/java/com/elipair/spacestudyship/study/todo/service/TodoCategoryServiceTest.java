package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.todo.dto.CategoryCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryUpdateRequest;
import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoCategoryServiceTest {

    @Mock TodoCategoryRepository categoryRepository;
    @Mock TodoRepository todoRepository;
    @Mock EntityManager entityManager;
    @InjectMocks TodoCategoryService categoryService;

    @Test
    @DisplayName("findAll: 사용자 카테고리 목록 반환")
    void findAll() {
        when(categoryRepository.findByUserIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(TodoCategory.create("c1", 1L, "수학", null, null, null)));

        List<CategoryResponse> result = categoryService.findAll(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 서버 UUID 생성")
    void create_serverId() {
        var request = new CategoryCreateRequest(null, "수학", "math_icon", 0.3, 0.5);
        when(categoryRepository.existsById(anyString())).thenReturn(false);
        when(categoryRepository.save(any(TodoCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = categoryService.create(1L, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.name()).isEqualTo("수학");
    }

    @Test
    @DisplayName("create: 동일 ID 있으면 CATEGORY_ALREADY_EXISTS")
    void create_duplicate() {
        var request = new CategoryCreateRequest("c1", "수학", null, null, null);
        when(categoryRepository.existsById("c1")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("update: 이름 변경 + 위치 변경 + iconId 유지")
    void update_partial() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "원본", "icon", 0.3, 0.5);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        var request = new CategoryUpdateRequest("새이름", null, 0.7, null);
        var response = categoryService.update(1L, "c1", request);

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.iconId()).isEqualTo("icon");
        assertThat(response.positionX()).isEqualTo(0.7);
        assertThat(response.positionY()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("update: 존재하지 않으면 CATEGORY_NOT_FOUND")
    void update_notFound() {
        when(categoryRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(Optional.empty());

        var request = new CategoryUpdateRequest("X", null, null, null);

        assertThatThrownBy(() -> categoryService.update(1L, "missing", request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("delete: 카테고리 삭제 + 연관 Todo의 categoryIds에서 제거")
    void delete_cascadesToTodos() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "수학", null, null, null);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        com.elipair.spacestudyship.study.todo.entity.Todo t1 =
                com.elipair.spacestudyship.study.todo.entity.Todo.create(
                        "t1", 1L, "X", null, List.of("c1", "c2"), null);
        com.elipair.spacestudyship.study.todo.entity.Todo t2 =
                com.elipair.spacestudyship.study.todo.entity.Todo.create(
                        "t2", 1L, "Y", null, List.of("c1"), null);
        when(todoRepository.findByUserIdAndCategoryId(1L, "\"c1\""))
                .thenReturn(List.of(t1, t2));

        categoryService.delete(1L, "c1");

        assertThat(t1.getCategoryIds()).containsExactly("c2");
        assertThat(t2.getCategoryIds()).isEmpty();
        org.mockito.Mockito.verify(categoryRepository).delete(existing);
    }

    @Test
    @DisplayName("delete: 존재하지 않으면 CATEGORY_NOT_FOUND")
    void delete_notFound() {
        when(categoryRepository.findByIdAndUserId("missing", 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(1L, "missing"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("create: save 후 EntityManager.flush() 호출 — createdAt/updatedAt 보장")
    void create_flushesAfterSave() {
        var request = new CategoryCreateRequest("c-new", "수학", "math_icon", 0.3, 0.5);
        when(categoryRepository.existsById("c-new")).thenReturn(false);
        when(categoryRepository.save(any(TodoCategory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(1L, request);

        verify(entityManager).flush();
    }

    @Test
    @DisplayName("update: mutation 후 EntityManager.flush() 호출 — updatedAt 갱신 보장")
    void update_flushesAfterMutation() {
        TodoCategory existing = TodoCategory.create("c1", 1L, "원본", "icon", 0.3, 0.5);
        when(categoryRepository.findByIdAndUserId("c1", 1L))
                .thenReturn(Optional.of(existing));

        var request = new CategoryUpdateRequest("새이름", null, null, null);
        categoryService.update(1L, "c1", request);

        verify(entityManager).flush();
    }
}
