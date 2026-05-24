package com.elipair.spacestudyship.controller.fuel;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.ErrorResponse;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fuel", description = "연료 잔량 및 거래 내역 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fuel")
public class FuelController {

    private final FuelService fuelService;

    @Operation(summary = "연료 잔량 조회",
            description = """
                현재 유저의 연료 잔량 및 누적 충전/소비량을 조회합니다.

                ### 응답 필드
                - currentFuel: 현재 보유 (totalCharged - totalConsumed)
                - totalCharged / totalConsumed: 누적량
                - pendingMinutes: 향후 확장용, 현재 항상 0
                - lastUpdatedAt: 마지막 변동 시각 (ISO 8601 UTC)
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FuelResponse.class),
                            examples = @ExampleObject(name = "Success",
                                    value = "{\"currentFuel\":350,\"totalCharged\":1200,\"totalConsumed\":850,\"pendingMinutes\":0,\"lastUpdatedAt\":\"2026-04-16T10:30:00Z\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<FuelResponse> getFuel(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(fuelService.getFuel(loginMember.memberId()));
    }

    @Operation(summary = "연료 거래 내역 조회",
            description = """
                연료 충전/소비 이력을 페이지네이션으로 조회합니다.

                ### Query Parameters
                - type: charge | consume (선택)
                - startDate / endDate: YYYY-MM-DD (선택, 종료일 포함 반열림 [start, end+1))
                - page: 기본 0
                - size: 기본 20, 최대 100

                정렬은 createdAt 내림차순 고정.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FuelTransactionListResponse.class),
                            examples = @ExampleObject(name = "Page",
                                    value = """
                                            {
                                              "content": [
                                                {"id":"tx-1","type":"charge","amount":90,"reason":"STUDY_SESSION","referenceId":"session-1","balanceAfter":350,"createdAt":"2026-04-16T10:30:00Z"}
                                              ],
                                              "page": 0, "size": 20, "totalElements": 120, "totalPages": 6
                                            }
                                            """))),
            @ApiResponse(responseCode = "400", description = "잘못된 query parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"type은 charge 또는 consume이어야 합니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/transactions")
    public ResponseEntity<FuelTransactionListResponse> getTransactions(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        if (type != null && !type.equals("charge") && !type.equals("consume")) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (startDate != null) {
            try { LocalDate.parse(startDate); } catch (DateTimeParseException e) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        if (endDate != null) {
            try { LocalDate.parse(endDate); } catch (DateTimeParseException e) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        TransactionType typeEnum = type == null ? null
                : TransactionType.valueOf(type.toUpperCase());
        return ResponseEntity.ok(
                fuelService.getTransactions(
                        loginMember.memberId(), typeEnum,
                        startDate, endDate, page, size));
    }
}
