package com.elipair.spacestudyship.member.entity;

import com.elipair.spacestudyship.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_members_social_id_type",
                        columnNames = {"social_id", "social_type"}
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SocialType socialType;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    public static Member signUp(String socialId, SocialType socialType, String nickname) {
        return Member.builder()
                .socialId(socialId)
                .socialType(socialType)
                .nickname(nickname)
                .build();
    }

    public void updateNickname(String modifiedNickname) {
        this.nickname = modifiedNickname;
    }
}
