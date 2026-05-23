package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, String> {

    List<Todo> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND scheduled_dates @> CAST(:dateJson AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndScheduledDate(@Param("userId") Long userId,
                                            @Param("dateJson") String dateJsonLiteral);

    @Query(value = """
            SELECT * FROM todos
            WHERE user_id = :userId
              AND category_ids @> CAST(:categoryJson AS jsonb)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Todo> findByUserIdAndCategoryId(@Param("userId") Long userId,
                                         @Param("categoryJson") String categoryJsonLiteral);

    boolean existsByIdAndUserId(String id, Long userId);

    Optional<Todo> findByIdAndUserId(String id, Long userId);
}
