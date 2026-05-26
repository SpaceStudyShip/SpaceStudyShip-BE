package com.elipair.spacestudyship.study.timer.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.dto.FuelChargeFromStudyResult;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import com.elipair.spacestudyship.study.timer.dto.*;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import com.elipair.spacestudyship.study.timer.repository.TimerSessionRepository;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TimerSessionService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 300;
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final TimerSessionRepository sessionRepository;
    private final FuelService fuelService;
    private final TodoService todoService;
    private final Clock clock;

    public TimerSessionService(TimerSessionRepository sessionRepository,
                               FuelService fuelService,
                               TodoService todoService,
                               Clock clock) {
        this.sessionRepository = sessionRepository;
        this.fuelService = fuelService;
        this.todoService = todoService;
        this.clock = clock;
    }

    @Transactional
    public TimerSessionCreateResponse create(
            Long userId, TimerSessionCreateRequest request, String idempotencyKey) {

        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null : idempotencyKey.trim();

        if (normalizedKey != null) {
            Optional<TimerSession> existing = sessionRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedKey);
            if (existing.isPresent()) {
                log.info("[Timer] idempotent skip | userId={}, key={}, sessionId={}",
                        userId, normalizedKey, existing.get().getId());
                int existingFuelCharged = fuelService.findChargedAmountBySessionId(existing.get().getId());
                return buildResponse(existing.get(), existingFuelCharged);
            }
        }

        LocalDateTime startedAtUtc = LocalDateTime.ofInstant(request.startedAt(), ZoneOffset.UTC);
        LocalDateTime endedAtUtc   = LocalDateTime.ofInstant(request.endedAt(),   ZoneOffset.UTC);
        validate(startedAtUtc, endedAtUtc, request.durationMinutes());

        String sessionId = UUID.randomUUID().toString();
        TimerSession session = TimerSession.of(
                sessionId, userId,
                request.todoId(), request.todoTitle(),
                startedAtUtc, endedAtUtc, request.durationMinutes(),
                normalizedKey);

        try {
            // saveAndFlush로 INSERT를 즉시 DB에 반영해 unique constraint 위반을
            // 이 try 블록 안에서 잡을 수 있도록 보장한다. 일반 save()는 flush를
            // 트랜잭션 커밋 시점까지 미루므로 race 복구 분기가 동작하지 않는다.
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey != null) {
                Optional<TimerSession> raced = sessionRepository
                        .findByUserIdAndIdempotencyKey(userId, normalizedKey);
                if (raced.isPresent()) {
                    log.info("[Timer] idempotent race resolved | userId={}, key={}",
                            userId, normalizedKey);
                    int racedFuelCharged = fuelService.findChargedAmountBySessionId(raced.get().getId());
                    return buildResponse(raced.get(), racedFuelCharged);
                }
            }
            throw e;
        }

        // 30분 = 1연료 환산. 잔여분은 user_fuel.pendingMinutes에 이월되어 다음 세션과 합산.
        FuelChargeFromStudyResult fuelResult = fuelService.chargeFromStudy(
                userId, request.durationMinutes(), sessionId);
        int fuelCharged = fuelResult.amount();

        // Todo actualMinutes는 실제 공부 분(durationMinutes)으로 누적 (연료 환산과 무관)
        if (request.todoId() != null) {
            todoService.addActualMinutes(userId, request.todoId(), request.durationMinutes());
        }

        log.info("[Timer] 세션 저장 | userId={}, sessionId={}, studyMinutes={}, fuelCharged={}, pendingMinutes={}, todoId={}",
                userId, sessionId, request.durationMinutes(), fuelCharged,
                fuelResult.newPendingMinutes(), request.todoId());
        return buildResponse(session, fuelCharged);
    }

    private void validate(LocalDateTime startedAt, LocalDateTime endedAt, int durationMinutes) {
        if (!endedAt.isAfter(startedAt)) {
            throw new CustomException(ErrorCode.INVALID_SESSION_TIME);
        }
        long elapsedMinutes = Duration.between(startedAt, endedAt).toMinutes();
        if (durationMinutes > elapsedMinutes) {
            throw new CustomException(ErrorCode.INVALID_DURATION);
        }
        if (durationMinutes < 1) {
            throw new CustomException(ErrorCode.SESSION_TOO_SHORT);
        }
        if (durationMinutes > 1440) {
            throw new CustomException(ErrorCode.SESSION_TOO_LONG);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (startedAt.isAfter(now.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS))) {
            throw new CustomException(ErrorCode.FUTURE_SESSION);
        }
    }

    private TimerSessionCreateResponse buildResponse(TimerSession session, int fuelCharged) {
        return new TimerSessionCreateResponse(TimerSessionResponse.from(session), fuelCharged);
    }

    public TimerSessionListResponse getList(
            Long userId, String startDate, String endDate, String todoId,
            int page, int size) {

        // 빈/공백 문자열은 null로 정규화 — `?todoId=` 같은 빈 파라미터를
        // "필터 미적용"으로 해석 (그대로 두면 JPQL `s.todoId = ''` 매칭으로 빈 결과 반환)
        String normalizedStartDate = blankToNull(startDate);
        String normalizedEndDate   = blankToNull(endDate);
        String normalizedTodoId    = blankToNull(todoId);

        LocalDateTime start = normalizedStartDate == null ? null
                : LocalDate.parse(normalizedStartDate).atStartOfDay();
        LocalDateTime end = normalizedEndDate == null ? null
                : LocalDate.parse(normalizedEndDate).plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<TimerSession> result = sessionRepository.findByFilters(
                userId, start, end, normalizedTodoId, pageable);
        return TimerSessionListResponse.from(result);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public TodayStatsResponse getTodayStats(Long userId) {
        LocalDate todayKst = LocalDate.now(clock.withZone(ZONE_KST));
        LocalDateTime todayStartUtc    = toUtcLdt(todayKst.atStartOfDay(ZONE_KST));
        LocalDateTime tomorrowStartUtc = toUtcLdt(todayKst.plusDays(1).atStartOfDay(ZONE_KST));

        long totalMinutes = Optional.ofNullable(
                sessionRepository.sumDurationBetween(userId, todayStartUtc, tomorrowStartUtc))
                .orElse(0L);
        long sessionCount = sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId, todayStartUtc, tomorrowStartUtc);

        LocalDateTime lookbackStart = toUtcLdt(
                todayKst.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(ZONE_KST));
        List<LocalDateTime> startedAts = sessionRepository
                .findStartedAtsAfter(userId, lookbackStart);

        int streak = computeStreak(startedAts, todayKst);
        // Task 3에서 lifetime/monthly 합산 로직으로 교체 — 현재는 컴파일 유지용 0 주입
        return new TodayStatsResponse(Math.toIntExact(totalMinutes), (int) sessionCount, streak, 0, 0, 0);
    }

    private LocalDateTime toUtcLdt(ZonedDateTime kst) {
        return kst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private int computeStreak(List<LocalDateTime> startedAtsUtc, LocalDate todayKst) {
        TreeSet<LocalDate> studyDays = startedAtsUtc.stream()
                .map(ts -> ts.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE_KST).toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));
        if (studyDays.isEmpty()) return 0;

        LocalDate latest = studyDays.last();
        LocalDate cursor = latest.isAfter(todayKst) ? todayKst : latest;
        if (cursor.isBefore(todayKst.minusDays(1))) return 0;

        int streak = 0;
        while (studyDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
