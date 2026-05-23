package com.elipair.spacestudyship.study.todo.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
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
class TodoCategoryRepositoryTest {

    @Autowired
    TodoCategoryRepository categoryRepository;

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtAsc: 사용자 카테고리를 생성일 오름차순으로 반환")
    void findByUserIdOrderByCreatedAtAsc() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        categoryRepository.save(TodoCategory.create("c2", 1L, "영어", null, null, null));
        categoryRepository.save(TodoCategory.create("c3", 2L, "다른유저", null, null, null));

        List<TodoCategory> result = categoryRepository.findByUserIdOrderByCreatedAtAsc(1L);

        assertThat(result).extracting("id").containsExactly("c1", "c2");
    }

    @Test
    @DisplayName("existsByIdAndUserId: 본인 카테고리는 true")
    void existsByIdAndUserId_true() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        assertThat(categoryRepository.existsByIdAndUserId("c1", 1L)).isTrue();
    }

    @Test
    @DisplayName("existsByIdAndUserId: 다른 사용자 카테고리는 false")
    void existsByIdAndUserId_otherUser() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        assertThat(categoryRepository.existsByIdAndUserId("c1", 2L)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndUserId: 본인 카테고리만 조회")
    void findByIdAndUserId() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));

        Optional<TodoCategory> mine = categoryRepository.findByIdAndUserId("c1", 1L);
        Optional<TodoCategory> other = categoryRepository.findByIdAndUserId("c1", 99L);

        assertThat(mine).isPresent();
        assertThat(other).isEmpty();
    }

    @Test
    @DisplayName("countByIdInAndUserId: 본인 소유 카테고리 ID 개수")
    void countByIdInAndUserId() {
        categoryRepository.save(TodoCategory.create("c1", 1L, "수학", null, null, null));
        categoryRepository.save(TodoCategory.create("c2", 1L, "영어", null, null, null));
        categoryRepository.save(TodoCategory.create("c3", 2L, "다른유저", null, null, null));

        long count = categoryRepository.countByIdInAndUserId(List.of("c1", "c2", "c3"), 1L);

        assertThat(count).isEqualTo(2L);
    }
}
