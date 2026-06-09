package com.elipair.spacestudyship.study.fuel.repository;

import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface FuelTransactionRepository extends JpaRepository<FuelTransaction, String> {

    /**
     * PostgreSQL JDBC 드라이버가 nullable 파라미터(enum/timestamp)의 타입을 추론하지 못하는 이슈로
     * `:param IS NULL` 대신 `CAST(:param AS Type) IS NULL` 패턴을 사용한다. CAST는 null 체크 분기에서만 사용되고
     * 실제 필터 비교(`ft.type = :type` 등)는 원래의 타입 바인딩 그대로 동작한다.
     */
    @Query("""
            SELECT ft FROM FuelTransaction ft
            WHERE ft.userId = :userId
              AND (CAST(:type AS String) IS NULL OR ft.type = :type)
              AND (CAST(:startDateTime AS LocalDateTime) IS NULL OR ft.createdAt >= :startDateTime)
              AND (CAST(:endDateTime AS LocalDateTime) IS NULL OR ft.createdAt < :endDateTime)
            """)
    Page<FuelTransaction> findByFilters(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            Pageable pageable);
}
