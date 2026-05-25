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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoCategoryService {

    private final TodoCategoryRepository categoryRepository;
    private final TodoRepository todoRepository;
    private final EntityManager entityManager;

    public List<CategoryResponse> findAll(Long userId) {
        return categoryRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (categoryRepository.existsById(id)) {
            throw new CustomException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }
        TodoCategory category = TodoCategory.create(
                id, userId, request.name(),
                request.iconId(), request.positionX(), request.positionY());
        TodoCategory saved = categoryRepository.save(category);
        entityManager.flush();
        log.info("[TodoCategory] 생성 | userId={}, categoryId={}", userId, saved.getId());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, String categoryId, CategoryUpdateRequest request) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        if (request.name() != null) category.updateName(request.name());
        if (request.iconId() != null) category.updateIconId(request.iconId());
        if (request.positionX() != null) category.updatePositionX(request.positionX());
        if (request.positionY() != null) category.updatePositionY(request.positionY());
        entityManager.flush();
        log.info("[TodoCategory] 수정 | userId={}, categoryId={}", userId, categoryId);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, String categoryId) {
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        List<com.elipair.spacestudyship.study.todo.entity.Todo> affected =
                todoRepository.findByUserIdAndCategoryId(userId, "\"" + categoryId + "\"");
        affected.forEach(todo -> todo.removeCategoryId(categoryId));
        // dirty checking으로 categoryIds 변경 자동 반영

        categoryRepository.delete(category);
        log.info("[TodoCategory] 삭제 | userId={}, categoryId={}, affectedTodos={}",
                userId, categoryId, affected.size());
    }
}
