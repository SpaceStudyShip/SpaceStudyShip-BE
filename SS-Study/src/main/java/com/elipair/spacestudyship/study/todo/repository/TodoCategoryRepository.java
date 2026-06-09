package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TodoCategoryRepository extends JpaRepository<TodoCategory, String> {

    List<TodoCategory> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByIdAndUserId(String id, Long userId);

    Optional<TodoCategory> findByIdAndUserId(String id, Long userId);

    long countByIdInAndUserId(Collection<String> ids, Long userId);
}
