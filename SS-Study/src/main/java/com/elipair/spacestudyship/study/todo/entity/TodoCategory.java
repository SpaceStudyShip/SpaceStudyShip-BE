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

@Entity
@Table(name = "todo_categories")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoCategory extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "icon_id", length = 50)
    private String iconId;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    public static TodoCategory create(String id, Long userId, String name,
                                      String iconId, Double positionX, Double positionY) {
        return TodoCategory.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .iconId(iconId)
                .positionX(positionX)
                .positionY(positionY)
                .build();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateIconId(String iconId) {
        this.iconId = iconId;
    }

    public void updatePositionX(Double positionX) {
        this.positionX = positionX;
    }

    public void updatePositionY(Double positionY) {
        this.positionY = positionY;
    }
}
