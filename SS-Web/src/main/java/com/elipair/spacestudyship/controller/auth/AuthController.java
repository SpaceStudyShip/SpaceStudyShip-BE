package com.elipair.spacestudyship.controller.auth;

import com.elipair.spacestudyship.auth.dto.CheckNicknameRequest;
import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.LoginRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.dto.LoginResponse;
import com.elipair.spacestudyship.auth.dto.LogoutRequest;
import com.elipair.spacestudyship.auth.dto.ReissueRequest;
import com.elipair.spacestudyship.auth.dto.ReissueResponse;
import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.auth.service.AuthService;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "소셜 로그인 및 토큰 관리 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "소셜 로그인",
            description = """
                    소셜 플랫폼(Firebase 등)에서 발급받은 ID Token을 백엔드에 전송하여 JWT를 발급받습니다.
                    해당 유저가 DB에 없으면 **자동으로 회원가입** 처리됩니다 (랜덤 닉네임 부여).

                    ### 응답 코드
                    - `200 OK` — 기존 회원 로그인 성공
                    - `201 Created` — 신규 회원 가입 + 로그인 성공 (클라이언트는 닉네임 설정 화면으로 이동 권장)

                    ### 인증 불필요
                    이 엔드포인트는 공개 API입니다. `Authorization` 헤더 없이 호출하세요.

                    ### 서버 처리 흐름
                    1. 소셜 ID Token 검증 (현재는 stub — 추후 Firebase Admin SDK 연동 예정)
                    2. socialType + socialId 로 DB 조회
                       - 존재: 기존 회원 정보로 JWT 발급
                       - 없음: 신규 회원 생성 (랜덤 닉네임), JWT 발급
                    3. Refresh Token 해시를 user_devices 테이블에 저장 (디바이스별)
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "기존 회원 로그인 성공.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    name = "ExistingMember",
                                    summary = "기존 회원 로그인",
                                    value = """
                                            {
                                              "memberId": 1,
                                              "nickname": "민첩한괴도5308",
                                              "tokens": {
                                                "accessToken": "eyJhbGciOiJIUzI1NiIs...",
                                                "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
                                              },
                                              "isNewMember": false
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "201",
                    description = "신규 회원 가입 + 로그인 성공. 응답 본문은 200과 동일 구조이며 `isNewMember: true`.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    name = "NewMember",
                                    summary = "신규 회원 가입",
                                    value = """
                                            {
                                              "memberId": 42,
                                              "nickname": "용감한고양이7321",
                                              "tokens": {
                                                "accessToken": "eyJhbGciOiJIUzI1NiIs...",
                                                "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
                                              },
                                              "isNewMember": true
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 형식 오류 (필수 필드 누락, socialType 이 유효하지 않은 값 등).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "InvalidInputValue",
                                            summary = "필수 필드 누락",
                                            value = """
                                                    {
                                                      "code": "INVALID_INPUT_VALUE",
                                                      "message": "idToken: 소셜 인증 토큰(ID Token)은 필수입니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "UnsupportedSocialType",
                                            summary = "지원하지 않는 소셜 타입",
                                            value = """
                                                    {
                                                      "code": "UNSUPPORTED_SOCIAL_TYPE",
                                                      "message": "지원하지 않는 소셜 로그인 방식입니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "소셜 ID Token 검증 실패 (토큰 만료, 변조, 발급자 불일치 등).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "SocialLoginFailed",
                                    value = """
                                            {
                                              "code": "SOCIAL_LOGIN_FAILED",
                                              "message": "소셜 로그인에 실패하였습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류. 닉네임 생성 재시도 초과 등.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        if (response.isNewMember()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "토큰 재발급",
            description = """
                    만료된 Access Token 을 Refresh Token 으로 재발급합니다.
                    Refresh Token 도 함께 갱신됩니다 (**Refresh Token Rotation**).

                    ### 인증 불필요
                    이 엔드포인트는 공개 API입니다. `Authorization` 헤더 대신 요청 본문의 `refreshToken` 으로 인증합니다.

                    ### 클라이언트 처리 흐름
                    1. 보호된 API 호출 → `401 UNAUTHORIZED` 수신
                    2. 본 엔드포인트 호출 (`refreshToken` 본문 전송)
                    3-a. 성공 (200): 새 Access/Refresh Token 저장 후 원래 API 재시도
                    3-b. 실패 (401 `INVALID_TOKEN`): 로그아웃 처리 + 로그인 화면 이동

                    ### 보안 정책
                    - Refresh Token 해시가 DB의 저장 해시와 불일치하면 **탈취 의심**으로 간주, 해당 디바이스 세션을 즉시 무효화한 뒤 401 응답.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 재발급 성공. 클라이언트는 두 토큰 모두 교체 저장해야 합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReissueResponse.class),
                            examples = @ExampleObject(
                                    name = "ReissueSuccess",
                                    value = """
                                            {
                                              "tokens": {
                                                "accessToken": "eyJhbGciOiJIUzI1NiIs...(new)",
                                                "refreshToken": "eyJhbGciOiJIUzI1NiIs...(new)"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 형식 오류 (refreshToken 누락 등).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidInputValue",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT_VALUE",
                                              "message": "refreshToken: Refresh Token은 필수입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token 이 만료되었거나, DB의 저장 해시와 불일치(탈취 의심)이거나, 변조된 경우. 클라이언트는 로그아웃 처리 후 로그인 화면으로 이동해야 합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidToken",
                                    value = """
                                            {
                                              "code": "INVALID_TOKEN",
                                              "message": "인증 정보가 올바르지 않습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@RequestBody @Valid ReissueRequest request) {
        return ResponseEntity.ok(authService.reissue(request));
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    서버에서 해당 디바이스의 Refresh Token 을 삭제(무효화)합니다.
                    클라이언트는 응답 수신 후 로컬에 저장된 Access/Refresh Token 도 함께 삭제해야 합니다.

                    ### 인증 불필요 (실제 동작상)
                    서버는 요청 본문의 `refreshToken` 에서 memberId 를 추출해 user_devices 테이블의 (member_id, device_id) row를 삭제합니다.
                    Refresh Token 이 유효하지 않거나 이미 삭제된 경우에도 멱등하게 **204** 를 응답합니다.

                    ### 단일 디바이스 로그아웃
                    Refresh Token 은 디바이스별로 발급되므로, 본 호출은 **현재 디바이스의 세션만** 종료합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "로그아웃 처리 완료. 응답 본문 없음.",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 형식 오류 (refreshToken 누락 등).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidInputValue",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT_VALUE",
                                              "message": "refreshToken: Refresh Token은 필수입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "닉네임 중복 확인",
            description = """
                    입력한 닉네임이 다른 사용자가 이미 사용 중인지 확인합니다.

                    ### 동작
                    - DB에 동일한 닉네임이 존재하지 않으면 `available: true`
                    - 이미 존재하면 `available: false`

                    ### 닉네임 규칙
                    - 길이: 2 ~ 10 자
                    - 허용 문자: 한글, 영문 대소문자, 숫자
                    - 금지: 공백, 특수문자, 이모지

                    ### 주의
                    - 본인이 현재 사용 중인 닉네임으로 조회해도 `available: false`로 응답됩니다. (프론트에서 본인 닉네임 입력 시 중복확인 버튼을 비활성화하는 것을 권장)
                    - 닉네임 변경 직전 마지막 검증으로 사용하되, 동시에 다른 사용자가 같은 닉네임을 등록하는 race condition 은 `PATCH /api/auth/nickname` 단계에서 별도로 처리됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정상 조회. 본문의 `available` 필드로 사용 가능 여부 판단.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CheckNicknameResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Available",
                                            summary = "사용 가능한 닉네임",
                                            value = """
                                                    {
                                                      "available": true
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "NotAvailable",
                                            summary = "이미 사용 중인 닉네임",
                                            value = """
                                                    {
                                                      "available": false
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "닉네임 형식 오류 (길이 미달/초과, 허용되지 않은 문자 포함 등). `message` 필드에 어떤 필드의 어떤 제약을 어겼는지 상세 표기됩니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidInputValue",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT_VALUE",
                                              "message": "nickname: 닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — Access Token 이 헤더에 없거나, 만료되었거나, 유효하지 않은 경우. 클라이언트는 `/api/auth/reissue` 로 재발급을 시도해야 합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthenticatedRequest",
                                    value = """
                                            {
                                              "code": "UNAUTHENTICATED_REQUEST",
                                              "message": "로그인이 필요합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류. 사용자에게는 \"잠시 후 다시 시도해주세요\" 안내가 적절합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/check-nickname")
    public ResponseEntity<CheckNicknameResponse> checkNickname(
            @AuthMember LoginMember loginMember,
            @ParameterObject @Valid @ModelAttribute CheckNicknameRequest request) {
        return ResponseEntity.ok(authService.checkNickname(request.nickname()));
    }

    @Operation(
            summary = "닉네임 변경",
            description = """
                    사용자의 닉네임을 변경합니다.

                    ### 동작
                    1. 닉네임 형식 검증 (길이, 허용 문자)
                    2. 본인 현재 닉네임과 동일하면 NO-OP — 중복 검사 없이 그대로 통과 (200)
                    3. 다른 회원이 사용 중이면 `409 DUPLICATED_NICKNAME`
                    4. 통과 시 DB 갱신 + JPA flush 로 unique 제약 위반을 동기적으로 감지 (race condition 처리)

                    ### 닉네임 규칙
                    - 길이: 2 ~ 10 자
                    - 허용 문자: 한글, 영문 대소문자, 숫자
                    - 금지: 공백, 특수문자, 이모지

                    ### 사전 검증
                    클라이언트는 입력 직후 `GET /api/auth/check-nickname` 로 사용 가능 여부를 먼저 확인하는 것을 권장합니다.
                    다만 이 호출 후 다른 사용자가 같은 닉네임을 차지하는 race 는 본 엔드포인트가 안전하게 처리합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "닉네임 변경 성공. 응답 본문에 변경된 닉네임 포함.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateNicknameResponse.class),
                            examples = @ExampleObject(
                                    name = "UpdateSuccess",
                                    value = """
                                            {
                                              "nickname": "우주탐험가"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "닉네임 형식 오류 (길이 미달/초과, 허용되지 않은 문자).",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InvalidInputValue",
                                    value = """
                                            {
                                              "code": "INVALID_INPUT_VALUE",
                                              "message": "nickname: 닉네임은 2자 이상 10자 이하여야 합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — Access Token 이 헤더에 없거나, 만료되었거나, 유효하지 않은 경우.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthenticatedRequest",
                                    value = """
                                            {
                                              "code": "UNAUTHENTICATED_REQUEST",
                                              "message": "로그인이 필요합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 다른 사용자가 사용 중인 닉네임.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "DuplicatedNickname",
                                    value = """
                                            {
                                              "code": "DUPLICATED_NICKNAME",
                                              "message": "이미 사용 중인 닉네임입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @PatchMapping("/nickname")
    public ResponseEntity<UpdateNicknameResponse> updateNickname(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid UpdateNicknameRequest request) {
        return ResponseEntity.ok(authService.updateNickname(loginMember.memberId(), request));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    인증된 사용자의 계정과 관련 데이터를 영구 삭제합니다. **이 작업은 되돌릴 수 없습니다.**

                    ### 삭제 대상
                    - `members` 테이블의 해당 회원 row
                    - `user_devices` 테이블의 해당 회원 row 전체 (FK CASCADE로 자동 삭제, 모든 디바이스 세션 무효화)
                    - Firebase Authentication 의 해당 사용자 (uid = 회원의 socialId)

                    ### 처리 순서
                    1. 회원 row 삭제 (`@Transactional`) → FK CASCADE로 user_devices 자동 삭제
                    2. Firebase Authentication 사용자 삭제

                    ### 멱등성
                    - 동일한 토큰으로 두 번 호출되거나, 다른 디바이스에서 먼저 탈퇴되어 회원이 이미 없는 상태에서 호출되어도 동일하게 **204**를 응답합니다.
                    - Firebase 측에서 사용자가 이미 없는 경우(`USER_NOT_FOUND`)도 무시하고 정상 완료 처리합니다.
                    - Firebase 일시 장애 등 외부 시스템 오류도 서버에서 로그만 남기고 클라이언트에는 **204**를 응답합니다 (우리 측 데이터 정리는 이미 완료).

                    ### 클라이언트 처리 가이드
                    - 응답 받은 후 로컬에 저장된 Access Token / Refresh Token / 회원 정보를 모두 삭제하고 로그인 화면으로 이동하세요.
                    - 네트워크 오류로 응답을 못 받은 경우 재시도 가능합니다 (멱등 보장).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "탈퇴 성공. 응답 본문 없음. (이미 탈퇴된 상태 / Firebase 측 사용자 부재 / 외부 시스템 일시 오류 등 모두 포함)",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — Access Token 이 헤더에 없거나, 만료되었거나, 유효하지 않은 경우. 클라이언트는 `/api/auth/reissue` 로 재발급을 시도해야 합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthenticatedRequest",
                                    value = """
                                            {
                                              "code": "UNAUTHENTICATED_REQUEST",
                                              "message": "로그인이 필요합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류. 주로 DB 통신 실패 시. 사용자에게는 \"잠시 후 다시 시도해주세요\" 안내가 적절합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthMember LoginMember loginMember) {
        authService.withdraw(loginMember.memberId());
        return ResponseEntity.noContent().build();
    }
}
