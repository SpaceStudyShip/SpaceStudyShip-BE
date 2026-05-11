package com.elipair.spacestudyship.auth.repository;

import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByMemberIdAndDeviceId(Long memberId, String deviceId);

    @Modifying
    void deleteByMemberIdAndDeviceId(Long memberId, String deviceId);

    long countByMemberId(Long memberId);

    default UserDevice getByMemberIdAndDeviceId(Long memberId, String deviceId) {
        return findByMemberIdAndDeviceId(memberId, deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
    }
}
