package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryCreateRequest;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.dto.CategoryUpdateRequest;
import com.elipair.spacestudyship.study.todo.service.TodoCategoryService;
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

@Tag(name = "TodoCategory", description = "할 일 카테고리 CRUD API")
@RestController
@RequestMapping("/api/todo-categories")
@RequiredArgsConstructor
@Validated
public class TodoCategoryController {

    private final TodoCategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "createdAt 오름차순")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class)))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}")))
    })
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll(@AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(categoryService.findAll(loginMember.memberId()));
    }

    @Operation(summary = "카테고리 생성")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"name: 비어있을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "동일 ID 중복",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_ALREADY_EXISTS\",\"message\":\"동일 ID의 카테고리가 이미 존재합니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(loginMember.memberId(), request));
    }

    @Operation(summary = "카테고리 부분 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"...\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId,
            @RequestBody @Valid CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryService.update(loginMember.memberId(), categoryId, request));
    }

    @Operation(summary = "카테고리 삭제",
            description = "삭제 시 연관 Todo의 categoryIds에서 자동 제거됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"UNAUTHENTICATED_REQUEST\",\"message\":\"로그인이 필요합니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"CATEGORY_NOT_FOUND\",\"message\":\"해당 카테고리를 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @AuthMember LoginMember loginMember,
            @PathVariable
            @Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryId: 영숫자와 하이픈만 허용합니다.")
            String categoryId) {
        categoryService.delete(loginMember.memberId(), categoryId);
        return ResponseEntity.noContent().build();
    }
}
