package com.elipair.spacestudyship.auth.controller;

import com.elipair.spacestudyship.auth.controller.dto.LoginRequest;
import com.elipair.spacestudyship.auth.controller.dto.LoginResponse;
import com.elipair.spacestudyship.auth.controller.dto.LogoutRequest;
import com.elipair.spacestudyship.auth.controller.dto.ReissueRequest;
import com.elipair.spacestudyship.auth.controller.dto.ReissueResponse;
import com.elipair.spacestudyship.auth.domain.Tokens;
import com.elipair.spacestudyship.auth.service.AuthService;
import com.elipair.spacestudyship.auth.service.dto.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "소셜 로그인 및 토큰 관리 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "소셜 로그인", description = "소셜 로그인을 통해 서비스에 로그인합니다. 신규 회원인 경우 자동 회원가입됩니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResult loginResult = authService.login(request.toCommand());
        LoginResponse response = LoginResponse.from(loginResult);

        if (loginResult.isNewMember()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access Token을 재발급합니다.")
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@RequestBody @Valid ReissueRequest request) {
        Tokens tokens = authService.reissueTokens(request.refreshToken());
        return ResponseEntity.ok(ReissueResponse.from(tokens));
    }

    @Operation(summary = "로그아웃", description = "로그아웃합니다. Refresh Token을 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
