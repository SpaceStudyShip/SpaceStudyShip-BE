package com.elipair.spacestudyship.study.todo.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "todos")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scheduled_dates", nullable = false, columnDefinition = "jsonb")
    private List<String> scheduledDates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_dates", nullable = false, columnDefinition = "jsonb")
    private List<String> completedDates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> categoryIds;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    public static Todo create(String id, Long userId, String title,
                              List<String> scheduledDates,
                              List<String> categoryIds,
                              Integer estimatedMinutes) {
        return Todo.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .scheduledDates(scheduledDates == null ? new ArrayList<>() : new ArrayList<>(scheduledDates))
                .completedDates(new ArrayList<>())
                .categoryIds(categoryIds == null ? new ArrayList<>() : new ArrayList<>(categoryIds))
                .estimatedMinutes(estimatedMinutes)
                .build();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateScheduledDates(List<String> dates) {
        this.scheduledDates = new ArrayList<>(dates);
    }

    public void updateCompletedDates(List<String> dates) {
        this.completedDates = new ArrayList<>(dates);
    }

    public void updateCategoryIds(List<String> ids) {
        this.categoryIds = new ArrayList<>(ids);
    }

    public void updateEstimatedMinutes(Integer minutes) {
        this.estimatedMinutes = minutes;
    }

    public void updateActualMinutes(Integer minutes) {
        this.actualMinutes = minutes;
    }

    public void removeCategoryId(String categoryId) {
        this.categoryIds = this.categoryIds.stream()
                .filter(id -> !java.util.Objects.equals(id, categoryId))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
