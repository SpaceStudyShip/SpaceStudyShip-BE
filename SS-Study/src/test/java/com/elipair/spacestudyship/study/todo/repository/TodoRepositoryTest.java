package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.todo.entity.Todo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class TodoRepositoryTest {

    @Autowired
    TodoRepository todoRepository;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc: 본인 Todo만, 최신순 반환")
    void findByUserId_ordered() {
        todoRepository.save(Todo.create("t1", 1L, "첫번째", null, null, null));
        todoRepository.save(Todo.create("t2", 1L, "두번째", null, null, null));
        todoRepository.save(Todo.create("t3", 2L, "다른유저", null, null, null));

        List<Todo> result = todoRepository.findByUserIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("userId").containsOnly(1L);
    }

    @Test
    @DisplayName("findByUserIdAndScheduledDate: JSONB @> 연산자로 날짜 포함 Todo 필터")
    void findByUserIdAndScheduledDate() {
        todoRepository.save(Todo.create("t1", 1L, "월요일", List.of("2026-04-16"), null, null));
        todoRepository.save(Todo.create("t2", 1L, "양일", List.of("2026-04-16", "2026-04-17"), null, null));
        todoRepository.save(Todo.create("t3", 1L, "다른날", List.of("2026-04-18"), null, null));

        List<Todo> result = todoRepository
                .findByUserIdAndScheduledDate(1L, "\"2026-04-16\"");

        assertThat(result).extracting("id").containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("findByUserIdAndCategoryId: JSONB @> 연산자로 카테고리 포함 Todo 필터")
    void findByUserIdAndCategoryId() {
        todoRepository.save(Todo.create("t1", 1L, "수학", null, List.of("c-math"), null));
        todoRepository.save(Todo.create("t2", 1L, "복합", null, List.of("c-math", "c-eng"), null));
        todoRepository.save(Todo.create("t3", 1L, "영어만", null, List.of("c-eng"), null));

        List<Todo> result = todoRepository
                .findByUserIdAndCategoryId(1L, "\"c-math\"");

        assertThat(result).extracting("id").containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("existsByIdAndUserId: 본인 소유 여부")
    void existsByIdAndUserId() {
        todoRepository.save(Todo.create("t1", 1L, "X", null, null, null));
        assertThat(todoRepository.existsByIdAndUserId("t1", 1L)).isTrue();
        assertThat(todoRepository.existsByIdAndUserId("t1", 99L)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndUserId: 본인 소유만 조회")
    void findByIdAndUserId() {
        todoRepository.save(Todo.create("t1", 1L, "X", null, null, null));

        Optional<Todo> mine = todoRepository.findByIdAndUserId("t1", 1L);
        Optional<Todo> other = todoRepository.findByIdAndUserId("t1", 99L);

        assertThat(mine).isPresent();
        assertThat(other).isEmpty();
    }
}
