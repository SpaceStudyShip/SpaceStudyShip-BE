package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserFuelRepository extends JpaRepository<UserFuel, Long> {

    Optional<UserFuel> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uf FROM UserFuel uf WHERE uf.userId = :userId")
    Optional<UserFuel> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}
