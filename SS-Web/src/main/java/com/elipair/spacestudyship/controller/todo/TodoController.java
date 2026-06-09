package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.todo.dto.TodoCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.dto.TodoUpdateRequest;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Todo", description = "할 일 CRUD API")
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
@Validated
public class TodoController {

    private final TodoService todoService;

    @Operation(summary = "할 일 목록 조회",
            description = "선택적으로 date / categoryId 쿼리로 필터. 결과는 createdAt 내림차순.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = TodoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}")))
    })
    @GetMapping
    public ResponseEntity<List<TodoResponse>> findAll(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date: YYYY-MM-DD 형식이어야 합니다.")
            String date,
            @RequestParam(required = false)
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId) {
        return ResponseEntity.ok(todoService.findAll(loginMember.memberId(), date, categoryId));
    }

    @Operation(summary = "할 일 생성",
            description = """
                    새 할 일을 생성합니다. id 미지정 시 서버가 UUID v4 생성.

                    ### 동작
                    1. id 충돌 검사 → 충돌 시 409 TODO_ALREADY_EXISTS
                    2. categoryIds 실존 검증 → 누락 시 404 CATEGORY_NOT_FOUND
                    3. 저장 후 생성된 객체 반환
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = TodoResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"title: 비어있을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "동일 ID 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_ALREADY_EXISTS\",\"message\":\"동일 ID의 할 일이 이미 존재합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PostMapping
    public ResponseEntity<TodoResponse> create(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid TodoCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoService.create(loginMember.memberId(), request));
    }

    @Operation(summary = "할 일 부분 수정",
            description = "전송하지 않은 필드는 기존 값 유지. 빈 배열은 명시적 모두 제거.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = TodoResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"...\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "Todo 없음 또는 다른 사용자 소유",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PatchMapping("/{todoId}")
    public ResponseEntity<TodoResponse> update(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "todoId: 영숫자와 하이픈만 허용합니다.")
            String todoId,
            @RequestBody @Valid TodoUpdateRequest request) {
        return ResponseEntity.ok(todoService.update(loginMember.memberId(), todoId, request));
    }

    @Operation(summary = "할 일 삭제", description = "본인 소유 Todo만 삭제 가능. 다른 사용자 / 없는 Todo는 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "Todo 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @DeleteMapping("/{todoId}")
    public ResponseEntity<Void> delete(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "todoId: 영숫자와 하이픈만 허용합니다.")
            String todoId) {
        todoService.delete(loginMember.memberId(), todoId);
        return ResponseEntity.noContent().build();
    }
}
