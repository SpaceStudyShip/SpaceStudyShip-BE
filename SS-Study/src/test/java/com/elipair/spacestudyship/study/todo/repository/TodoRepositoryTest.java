package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.todo.entity.Todo;
import jakarta.persistence.EntityManager;
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

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc: 본인 Todo만, 최신순 반환 (id 순서까지 검증)")
    void findByUserId_ordered() throws InterruptedException {
        todoRepository.saveAndFlush(Todo.create("t1", 1L, "첫번째", null, null, null));
        Thread.sleep(5);
        todoRepository.saveAndFlush(Todo.create("t2", 1L, "두번째", null, null, null));
        todoRepository.saveAndFlush(Todo.create("t3", 2L, "다른유저", null, null, null));

        List<Todo> result = todoRepository.findByUserIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting("userId").containsOnly(1L);
        assertThat(result).extracting("id").containsExactly("t2", "t1");
    }

    @Test
    @DisplayName("findByUserIdAndScheduledDate: JSONB @> 필터 + 타 사용자 격리")
    void findByUserIdAndScheduledDate() {
        todoRepository.save(Todo.create("t1", 1L, "월요일", List.of("2026-04-16"), null, null));
        todoRepository.save(Todo.create("t2", 1L, "양일", List.of("2026-04-16", "2026-04-17"), null, null));
        todoRepository.save(Todo.create("t3", 1L, "다른날", List.of("2026-04-18"), null, null));
        // 타 사용자의 같은 날짜 Todo — user_id 조건 누락 회귀 방지
        todoRepository.save(Todo.create("t4", 2L, "다른유저_같은날", List.of("2026-04-16"), null, null));

        List<Todo> result = todoRepository
                .findByUserIdAndScheduledDate(1L, "\"2026-04-16\"");

        assertThat(result).extracting("id").containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    @DisplayName("findByUserIdAndCategoryId: JSONB @> 필터 + 타 사용자 격리")
    void findByUserIdAndCategoryId() {
        todoRepository.save(Todo.create("t1", 1L, "수학", null, List.of("c-math"), null));
        todoRepository.save(Todo.create("t2", 1L, "복합", null, List.of("c-math", "c-eng"), null));
        todoRepository.save(Todo.create("t3", 1L, "영어만", null, List.of("c-eng"), null));
        // 타 사용자의 같은 카테고리 Todo — user_id 조건 누락 회귀 방지
        todoRepository.save(Todo.create("t4", 2L, "다른유저_같은카테고리", null, List.of("c-math"), null));

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

    @Test
    @DisplayName("saveAndFlush: assigned-ID Todo의 timestamp가 flush 후 채워짐 (Hibernate 회귀 알람)")
    void saveAndFlush_populatesTimestamps() {
        Todo saved = todoRepository.saveAndFlush(Todo.create("t-ts", 1L, "X", null, null, null));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("addActualMinutes: 본인 todo 누적 — null → 0+minutes, 기존 → 기존+minutes")
    void addActualMinutes_accumulates() {
        Todo t = Todo.create(
                "t-1", 1L, "수학",
                List.of("2026-05-25"),
                List.of(),
                60);
        todoRepository.saveAndFlush(t);

        int updated1 = todoRepository.addActualMinutes(1L, "t-1", 30);
        assertThat(updated1).isEqualTo(1);
        em.clear();
        assertThat(todoRepository.findById("t-1").get().getActualMinutes()).isEqualTo(30);

        int updated2 = todoRepository.addActualMinutes(1L, "t-1", 45);
        assertThat(updated2).isEqualTo(1);
        em.clear();
        assertThat(todoRepository.findById("t-1").get().getActualMinutes()).isEqualTo(75);
    }

    @Test
    @DisplayName("addActualMinutes: 본인 소유 아님 → affected=0")
    void addActualMinutes_otherUser_returnsZero() {
        Todo t = Todo.create(
                "t-1", 1L, "수학",
                List.of("2026-05-25"),
                List.of(),
                60);
        todoRepository.saveAndFlush(t);

        int updated = todoRepository.addActualMinutes(2L, "t-1", 30);
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("addActualMinutes: 없는 todoId → affected=0")
    void addActualMinutes_missingTodo_returnsZero() {
        int updated = todoRepository.addActualMinutes(1L, "nope", 30);
        assertThat(updated).isZero();
    }
}
