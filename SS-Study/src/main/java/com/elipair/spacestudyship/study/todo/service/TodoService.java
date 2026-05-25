package com.elipair.spacestudyship.study.todo.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.entity.Todo;
import com.elipair.spacestudyship.study.todo.repository.TodoCategoryRepository;
import com.elipair.spacestudyship.study.todo.repository.TodoRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoCategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public List<TodoResponse> findAll(Long userId, String date, String categoryId) {
        List<Todo> todos;
        if (date != null && categoryId != null) {
            Set<String> byDateIds = todoRepository
                    .findByUserIdAndScheduledDate(userId, jsonLiteral(date))
                    .stream()
                    .map(Todo::getId)
                    .collect(Collectors.toSet());
            todos = todoRepository
                    .findByUserIdAndCategoryId(userId, jsonLiteral(categoryId))
                    .stream()
                    .filter(t -> byDateIds.contains(t.getId()))
                    .toList();
        } else if (date != null) {
            todos = todoRepository.findByUserIdAndScheduledDate(userId, jsonLiteral(date));
        } else if (categoryId != null) {
            todos = todoRepository.findByUserIdAndCategoryId(userId, jsonLiteral(categoryId));
        } else {
            todos = todoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return todos.stream().map(TodoResponse::from).toList();
    }

    private static String jsonLiteral(String value) {
        return "\"" + value + "\"";
    }

    @Transactional
    public TodoResponse create(Long userId, TodoCreateRequest request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        if (todoRepository.existsById(id)) {
            throw new CustomException(ErrorCode.TODO_ALREADY_EXISTS);
        }
        validateCategoryIds(userId, request.categoryIds());

        Todo todo = Todo.create(
                id, userId, request.title(),
                request.scheduledDates(),
                request.categoryIds(),
                request.estimatedMinutes());
        Todo saved = todoRepository.save(todo);
        entityManager.flush();
        log.info("[Todo] 생성 | userId={}, todoId={}", userId, saved.getId());
        return TodoResponse.from(saved);
    }

    private void validateCategoryIds(Long userId, List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return;
        List<String> distinct = categoryIds.stream().distinct().toList();
        long found = categoryRepository.countByIdInAndUserId(distinct, userId);
        if (found != distinct.size()) {
            throw new CustomException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Transactional
    public TodoResponse update(Long userId, String todoId,
                               com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest request) {
        Todo todo = todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TODO_NOT_FOUND));

        if (request.categoryIds() != null) {
            validateCategoryIds(userId, request.categoryIds());
            todo.updateCategoryIds(request.categoryIds());
        }
        if (request.title() != null) todo.updateTitle(request.title());
        if (request.scheduledDates() != null) todo.updateScheduledDates(request.scheduledDates());
        if (request.completedDates() != null) todo.updateCompletedDates(request.completedDates());
        if (request.estimatedMinutes() != null) todo.updateEstimatedMinutes(request.estimatedMinutes());
        if (request.actualMinutes() != null) todo.updateActualMinutes(request.actualMinutes());

        entityManager.flush();
        log.info("[Todo] 수정 | userId={}, todoId={}", userId, todoId);
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(Long userId, String todoId) {
        long deleted = todoRepository.deleteByIdAndUserId(todoId, userId);
        if (deleted == 0) {
            throw new CustomException(ErrorCode.TODO_NOT_FOUND);
        }
        log.info("[Todo] 삭제 | userId={}, todoId={}", userId, todoId);
    }

    @Transactional
    public void addActualMinutes(Long userId, String todoId, int minutes) {
        if (minutes <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int updated = todoRepository.addActualMinutes(userId, todoId, minutes);
        if (updated == 0) {
            throw new CustomException(ErrorCode.TODO_NOT_FOUND);
        }
        log.info("[Todo] actualMinutes 누적 | userId={}, todoId={}, +{}분", userId, todoId, minutes);
    }
}
