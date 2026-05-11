package com.elipair.spacestudyship.auth.entity;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_devices_member_device",
                columnNames = {"member_id", "device_id"}
        ),
        indexes = @Index(name = "idx_user_devices_member", columnList = "member_id")
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(name = "fcm_token", nullable = false, length = 255)
    private String fcmToken;

    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshTokenHash;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    public static UserDevice register(Long memberId, String deviceId, DeviceType deviceType,
                                      String fcmToken, String refreshTokenHash) {
        return UserDevice.builder()
                .memberId(memberId)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .fcmToken(fcmToken)
                .refreshTokenHash(refreshTokenHash)
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    public void renewLogin(DeviceType deviceType, String fcmToken, String refreshTokenHash) {
        this.deviceType = deviceType;
        this.fcmToken = fcmToken;
        this.refreshTokenHash = refreshTokenHash;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void rotateRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }
}
