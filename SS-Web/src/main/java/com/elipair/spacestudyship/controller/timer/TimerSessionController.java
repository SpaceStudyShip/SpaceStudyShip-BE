package com.elipair.spacestudyship.controller.timer;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateRequest;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionListResponse;
import com.elipair.spacestudyship.study.timer.dto.TodayStatsResponse;
import com.elipair.spacestudyship.study.timer.service.TimerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Tag(name = "Timer", description = "공부 타이머 세션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timer-sessions")
public class TimerSessionController {

    private final TimerSessionService timerSessionService;

    @Operation(summary = "세션 기록 저장",
            description = """
                타이머 종료 시 세션을 저장합니다.
                서버에서 시간 유효성 5단계 검증 후, 통과 시 연료를 자동 충전하고
                연결된 Todo의 actualMinutes를 누적합니다 (단일 트랜잭션).

                ### Idempotency
                헤더 `Idempotency-Key`를 보내면 동일 키 재요청 시 기존 세션을 반환합니다 (중복 충전 방지).
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TimerSessionCreateResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "session": {
                                        "id":"sess-uuid",
                                        "todoId":"todo-1",
                                        "todoTitle":"수학",
                                        "startedAt":"2026-05-25T01:00:00Z",
                                        "endedAt":"2026-05-25T02:30:00Z",
                                        "durationMinutes":90
                                      },
                                      "fuelCharged":90
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "INVALID_SESSION_TIME", value = "{\"code\":\"INVALID_SESSION_TIME\",\"message\":\"시작 시각이 종료 시각보다 늦거나 같습니다.\"}"),
                                    @ExampleObject(name = "INVALID_DURATION",     value = "{\"code\":\"INVALID_DURATION\",\"message\":\"공부 시간이 시작/종료 시각 간격보다 큽니다.\"}"),
                                    @ExampleObject(name = "SESSION_TOO_SHORT",    value = "{\"code\":\"SESSION_TOO_SHORT\",\"message\":\"공부 시간은 1분 이상이어야 합니다.\"}"),
                                    @ExampleObject(name = "SESSION_TOO_LONG",     value = "{\"code\":\"SESSION_TOO_LONG\",\"message\":\"공부 시간은 24시간(1440분)을 초과할 수 없습니다.\"}"),
                                    @ExampleObject(name = "FUTURE_SESSION",       value = "{\"code\":\"FUTURE_SESSION\",\"message\":\"미래 시각의 세션은 저장할 수 없습니다.\"}")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "연결된 Todo가 본인 소유 아님 / 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<TimerSessionCreateResponse> create(
            @AuthMember LoginMember loginMember,
            @Valid @RequestBody TimerSessionCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        TimerSessionCreateResponse response = timerSessionService.create(
                loginMember.memberId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "세션 목록 조회",
            description = """
                ### Query Parameters
                - startDate / endDate: YYYY-MM-DD (선택). 종료일 포함 반열림 [start, end+1)
                - todoId: 특정 Todo에 연결된 세션만 (선택)
                - page: 기본 0
                - size: 기본 20, 최대 100

                정렬: startedAt 내림차순 (최신순) 고정.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TimerSessionListResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 query parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"입력값이 유효하지 않습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<TimerSessionListResponse> getList(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String todoId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        validateDateParam(startDate);
        validateDateParam(endDate);
        if (page < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (size < 1 || size > 100) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        return ResponseEntity.ok(timerSessionService.getList(
                loginMember.memberId(), startDate, endDate, todoId, page, size));
    }

    @Operation(summary = "오늘 공부 통계",
            description = "KST(Asia/Seoul) 기준 오늘의 총 분 / 세션 수 / 연속 일수(streak)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TodayStatsResponse.class),
                            examples = @ExampleObject(value = "{\"totalMinutes\":180,\"sessionCount\":3,\"streak\":7}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/today-stats")
    public ResponseEntity<TodayStatsResponse> getTodayStats(
            @AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(
                timerSessionService.getTodayStats(loginMember.memberId()));
    }

    private void validateDateParam(String date) {
        if (date == null) return;
        try { LocalDate.parse(date); }
        catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
