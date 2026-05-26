package com.elipair.spacestudyship.study.timer.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.dto.FuelChargeFromStudyResult;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateRequest;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionListResponse;
import com.elipair.spacestudyship.study.timer.dto.TodayStatsResponse;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import com.elipair.spacestudyship.study.timer.repository.TimerSessionRepository;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimerSessionServiceTest {

    @Mock TimerSessionRepository sessionRepository;
    @Mock FuelService fuelService;
    @Mock TodoService todoService;

    TimerSessionService service;

    Clock fixedClock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new TimerSessionService(sessionRepository, fuelService, todoService, fixedClock);
    }

    private TimerSessionCreateRequest validRequest(int duration) {
        return new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                duration);
    }

    @Test
    @DisplayName("validate: startedAt == endedAt → INVALID_SESSION_TIME")
    void validate_sameTime_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T01:00:00Z"),
                1);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SESSION_TIME);
    }

    @Test
    @DisplayName("validate: durationMinutes > 경과시간 → INVALID_DURATION")
    void validate_durationOverElapsed_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T01:30:00Z"),
                31);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DURATION);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    @DisplayName("validate: durationMinutes < 1 → SESSION_TOO_SHORT")
    void validate_tooShort_throws(int duration) {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T03:00:00Z"),
                duration);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_TOO_SHORT);
    }

    @Test
    @DisplayName("validate: durationMinutes > 1440 → SESSION_TOO_LONG")
    void validate_tooLong_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-25T01:00:00Z"),
                1441);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_TOO_LONG);
    }

    @Test
    @DisplayName("validate: startedAt > now + 5분 → FUTURE_SESSION")
    void validate_future_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T12:05:01Z"),
                Instant.parse("2026-05-25T13:00:00Z"),
                30);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUTURE_SESSION);
    }

    @Test
    @DisplayName("validate: startedAt == now + 5분 정확히 → 통과")
    void validate_exactlyAtSkewBoundary_passes() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T12:05:00Z"),
                Instant.parse("2026-05-25T13:00:00Z"),
                30);
        given(fuelService.chargeFromStudy(eq(1L), eq(30), anyString()))
                .willReturn(new FuelChargeFromStudyResult(1, 0, 1));

        TimerSessionCreateResponse res = service.create(1L, req, null);
        assertThat(res.session().durationMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("create 정상: 세션 저장 + Fuel chargeFromStudy(60분 → 2연료) + (todoId 없으므로) Todo 미호출")
    void create_noTodo_chargesFuel_doesNotTouchTodo() {
        TimerSessionCreateRequest req = validRequest(60);
        // 60분 = 2연료 (30분=1연료 환산), pending 0
        given(fuelService.chargeFromStudy(eq(1L), eq(60), anyString()))
                .willReturn(new FuelChargeFromStudyResult(2, 0, 2));

        TimerSessionCreateResponse res = service.create(1L, req, null);

        ArgumentCaptor<TimerSession> savedCap = ArgumentCaptor.forClass(TimerSession.class);
        verify(sessionRepository).saveAndFlush(savedCap.capture());
        TimerSession saved = savedCap.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getIdempotencyKey()).isNull();
        assertThat(saved.getId()).isNotBlank();

        verify(fuelService).chargeFromStudy(eq(1L), eq(60), eq(saved.getId()));

        verifyNoInteractions(todoService);

        assertThat(res.fuelCharged()).isEqualTo(2);
        assertThat(res.session().id()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("create 정상: 25분 세션 → fuelCharged=0 (pending 누적), Todo는 25분 그대로 누적")
    void create_under30Min_noFuelChargedButPendingAccumulated() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                "todo-1", "수학",
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T01:25:00Z"),
                25);
        // 25분 → amount=0, pending=25 (30분 미만이라 fuel transaction 생성 안 됨)
        given(fuelService.chargeFromStudy(eq(1L), eq(25), anyString()))
                .willReturn(new FuelChargeFromStudyResult(0, 25, 0));

        TimerSessionCreateResponse res = service.create(1L, req, null);

        assertThat(res.fuelCharged()).isZero();
        // Todo는 실제 공부 분 그대로 누적 (연료 환산과 무관)
        verify(todoService).addActualMinutes(eq(1L), eq("todo-1"), eq(25));
    }

    @Test
    @DisplayName("create 정상: todoId 있으면 TodoService.addActualMinutes 호출 (studyMinutes 그대로)")
    void create_withTodo_callsAddActualMinutes() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                "todo-1", "수학",
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                60);
        given(fuelService.chargeFromStudy(eq(1L), eq(60), anyString()))
                .willReturn(new FuelChargeFromStudyResult(2, 0, 2));

        service.create(1L, req, null);

        verify(todoService).addActualMinutes(eq(1L), eq("todo-1"), eq(60));
    }

    @Test
    @DisplayName("Idempotency-Key dedup: 동일 키 재요청 시 기존 세션 반환 + fuel transaction 조회로 fuelCharged 복구")
    void idempotency_dedup_returnsExisting() {
        TimerSession existing = TimerSession.of(
                "existing-id", 1L, null, null,
                LocalDateTime.parse("2026-05-25T01:00:00"),
                LocalDateTime.parse("2026-05-25T02:00:00"),
                60, "idem-1");
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.of(existing));
        // dedup 시 fuelCharged는 기존 fuel transaction 조회로 복구 (60분 → 2연료)
        given(fuelService.findChargedAmountBySessionId("existing-id")).willReturn(2);

        TimerSessionCreateResponse res = service.create(1L, validRequest(60), "idem-1");

        verify(sessionRepository, never()).saveAndFlush(any());
        // chargeFromStudy는 호출 안 됨 (실제 충전·중복 차단)
        verify(fuelService, never()).chargeFromStudy(anyLong(), anyInt(), anyString());
        verifyNoInteractions(todoService);
        assertThat(res.session().id()).isEqualTo("existing-id");
        assertThat(res.fuelCharged()).isEqualTo(2);
    }

    @Test
    @DisplayName("Idempotency-Key 정규화: blank → null로 취급 (dedup 안 함)")
    void idempotency_blank_normalizedToNull() {
        given(fuelService.chargeFromStudy(eq(1L), eq(60), anyString()))
                .willReturn(new FuelChargeFromStudyResult(2, 0, 2));

        service.create(1L, validRequest(60), "   ");

        verify(sessionRepository, never()).findByUserIdAndIdempotencyKey(anyLong(), any());
        ArgumentCaptor<TimerSession> cap = ArgumentCaptor.forClass(TimerSession.class);
        verify(sessionRepository).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("Idempotency race: saveAndFlush 시 DataIntegrityViolation → 재조회 후 기존 반환 + fuel transaction 조회")
    void idempotency_race_resolvedByReSelect() {
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(TimerSession.of(
                        "racer-id", 1L, null, null,
                        LocalDateTime.parse("2026-05-25T01:00:00"),
                        LocalDateTime.parse("2026-05-25T02:00:00"),
                        60, "idem-1")));
        given(sessionRepository.saveAndFlush(any(TimerSession.class)))
                .willThrow(new DataIntegrityViolationException("unique violation"));
        given(fuelService.findChargedAmountBySessionId("racer-id")).willReturn(2);

        TimerSessionCreateResponse res = service.create(1L, validRequest(60), "idem-1");

        assertThat(res.session().id()).isEqualTo("racer-id");
        assertThat(res.fuelCharged()).isEqualTo(2);
        // race 복구 후에는 신규 chargeFromStudy 호출 안 함
        verify(fuelService, never()).chargeFromStudy(anyLong(), anyInt(), anyString());
        verifyNoInteractions(todoService);
    }

    @Test
    @DisplayName("Idempotency race: saveAndFlush 실패했는데 재조회도 empty → 원본 예외 rethrow")
    void idempotency_race_rethrowIfStillMissing() {
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.empty());
        given(sessionRepository.saveAndFlush(any(TimerSession.class)))
                .willThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.create(1L, validRequest(60), "idem-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("getList: 필터 인자가 서비스 → 레포로 전달, Page envelope 변환")
    void getList_passThroughAndEnvelope() {
        TimerSession s = TimerSession.of(
                UUID.randomUUID().toString(), 1L, "t-1", "title",
                LocalDateTime.parse("2026-05-25T01:00:00"),
                LocalDateTime.parse("2026-05-25T02:00:00"),
                60, null);
        Page<TimerSession> page = new PageImpl<>(List.of(s));
        given(sessionRepository.findByFilters(eq(1L), any(), any(), eq("t-1"), any(Pageable.class)))
                .willReturn(page);

        TimerSessionListResponse res = service.getList(
                1L, "2026-05-20", "2026-05-25", "t-1", 0, 20);

        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).id()).isEqualTo(s.getId());
    }

    @Test
    @DisplayName("getList: 빈/공백 파라미터는 null로 정규화되어 필터 미적용")
    void getList_blankParamsNormalizedToNull() {
        Page<TimerSession> empty = new PageImpl<>(List.of());
        given(sessionRepository.findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(empty);

        // todoId/startDate/endDate 모두 blank
        service.getList(1L, "", "  ", "", 0, 20);

        // 정규화된 null이 레포로 전달됨
        verify(sessionRepository).findByFilters(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("today-stats: 빈 데이터 → 모두 0")
    void todayStats_empty() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res).isEqualTo(new TodayStatsResponse(0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("today-stats: 정상 데이터 + streak 계산")
    void todayStats_withData() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(180L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(3L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-25T02:00:00"),
                        LocalDateTime.parse("2026-05-23T16:00:00"),
                        LocalDateTime.parse("2026-05-22T16:00:00")
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.totalMinutes()).isEqualTo(180);
        assertThat(res.sessionCount()).isEqualTo(3);
        assertThat(res.streak()).isEqualTo(3);
    }

    @Test
    @DisplayName("streak: 어제까지만 했으면 어제 기준으로 N (오늘 포함 X)")
    void streak_yesterdayLatest() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-23T16:00:00"),
                        LocalDateTime.parse("2026-05-22T16:00:00")
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.streak()).isEqualTo(2);
    }

    @Test
    @DisplayName("streak: 마지막 공부일이 어제보다 이전 → 0")
    void streak_brokenChain() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(LocalDateTime.parse("2026-05-22T16:00:00")));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.streak()).isZero();
    }

    @Test
    @DisplayName("streak: latest가 미래(clock skew)면 today로 클램프")
    void streak_futureLatest_clampedToToday() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-26T01:00:00"),
                        LocalDateTime.parse("2026-05-25T01:00:00"),
                        LocalDateTime.parse("2026-05-23T16:00:00")
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.streak()).isEqualTo(2);
    }
}
