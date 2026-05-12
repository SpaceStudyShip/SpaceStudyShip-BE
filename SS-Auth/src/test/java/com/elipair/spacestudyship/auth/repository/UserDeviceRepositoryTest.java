package com.elipair.spacestudyship.auth.repository;

import com.elipair.spacestudyship.auth.TestAuthApplication;
import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestAuthApplication.class)
@Transactional
class UserDeviceRepositoryTest {

    @Autowired
    UserDeviceRepository userDeviceRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("findByMemberIdAndDeviceId: 존재하는 row 조회")
    void findByMemberIdAndDeviceId_found() {
        UserDevice saved = userDeviceRepository.save(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-token-1", "refresh-1"));

        Optional<UserDevice> found = userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-1");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getFcmToken()).isEqualTo("fcm-token-1");
    }

    @Test
    @DisplayName("findByMemberIdAndDeviceId: 다른 deviceId면 Optional.empty")
    void findByMemberIdAndDeviceId_notFound() {
        userDeviceRepository.save(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-token-1", "refresh-1"));

        Optional<UserDevice> found = userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-999");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("getByMemberIdAndDeviceId: 없으면 INVALID_TOKEN 예외")
    void getByMemberIdAndDeviceId_throws() {
        assertThatThrownBy(() -> userDeviceRepository.getByMemberIdAndDeviceId(1L, "missing"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("deleteByMemberIdAndDeviceId: 해당 row만 삭제, 다른 row는 유지")
    void deleteByMemberIdAndDeviceId_onlyTargetDeleted() {
        userDeviceRepository.save(UserDevice.register(
                1L, "device-A", DeviceType.IOS, "fcm-A", "refresh-A"));
        userDeviceRepository.save(UserDevice.register(
                1L, "device-B", DeviceType.ANDROID, "fcm-B", "refresh-B"));

        userDeviceRepository.deleteByMemberIdAndDeviceId(1L, "device-A");
        userDeviceRepository.flush();

        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-A")).isEmpty();
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "device-B")).isPresent();
    }

    @Test
    @DisplayName("(member_id, device_id) 컴포지트 unique 위반 시 DataIntegrityViolationException")
    void uniqueConstraint_violation() {
        userDeviceRepository.saveAndFlush(UserDevice.register(
                1L, "device-1", DeviceType.IOS, "fcm-1", "refresh-1"));

        assertThatThrownBy(() -> userDeviceRepository.saveAndFlush(UserDevice.register(
                1L, "device-1", DeviceType.ANDROID, "fcm-2", "refresh-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 device_id라도 member_id 다르면 별개 row로 공존")
    void sameDeviceIdDifferentMember_coexist() {
        userDeviceRepository.save(UserDevice.register(
                1L, "shared-device", DeviceType.IOS, "fcm-A", "refresh-A"));
        userDeviceRepository.save(UserDevice.register(
                2L, "shared-device", DeviceType.IOS, "fcm-B", "refresh-B"));

        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(1L, "shared-device")).isPresent();
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(2L, "shared-device")).isPresent();
    }

    @Test
    @DisplayName("members 삭제 시 user_devices의 해당 row가 CASCADE로 자동 삭제된다")
    @Transactional
    @DirtiesContext
    void cascadeDelete_onMemberDelete() {
        // H2 테스트 스키마에 ON DELETE CASCADE FK를 추가 (마이그레이션 SQL V0_0_31의 FK 검증)
        // @DirtiesContext: DDL은 트랜잭션 롤백 대상이 아니므로 컨텍스트를 재생성해 다른 테스트에 영향 없도록 함
        jdbcTemplate.execute(
                "ALTER TABLE user_devices DROP CONSTRAINT IF EXISTS fk_user_devices_member_cascade");
        jdbcTemplate.execute(
                "ALTER TABLE user_devices ADD CONSTRAINT fk_user_devices_member_cascade " +
                "FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE");

        // given
        Member member = Member.signUp("social-cascade", SocialType.GOOGLE, "캐스케이드테스터");
        entityManager.persist(member);
        entityManager.flush();

        userDeviceRepository.save(UserDevice.register(
                member.getId(), "device-cascade", DeviceType.IOS, "fcm-cascade", "hash-cascade"));
        userDeviceRepository.flush();
        entityManager.clear();

        // when - members row 삭제 → FK CASCADE로 user_devices row 자동 삭제
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", member.getId());

        // then
        assertThat(userDeviceRepository.findByMemberIdAndDeviceId(member.getId(), "device-cascade"))
                .isEmpty();
    }
}
