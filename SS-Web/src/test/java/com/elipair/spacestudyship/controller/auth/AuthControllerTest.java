package com.elipair.spacestudyship.controller.auth;

import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMemberArgumentResolver;
import com.elipair.spacestudyship.auth.service.AuthService;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService authService;

    MockMvc mockMvc;

    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========== GET /api/auth/check-nickname ==========

    @Test
    @DisplayName("checkNickname: 정상 요청이면 200과 available 반환")
    void checkNickname_success() throws Exception {
        // given
        given(authService.checkNickname("우주탐험가"))
                .willReturn(new CheckNicknameResponse(true));

        // when / then
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주탐험가")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @DisplayName("checkNickname: nickname 파라미터가 없으면 400")
    void checkNickname_missingParam() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 1자(길이 미달)이면 400")
    void checkNickname_tooShort() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "가")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 11자(길이 초과)이면 400")
    void checkNickname_tooLong() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "일이삼사오육칠팔구십일")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 특수문자가 포함되면 400")
    void checkNickname_invalidCharacter() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주!탐험")
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("checkNickname: 인증 정보가 없으면 401")
    void checkNickname_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/check-nickname")
                        .param("nickname", "우주탐험가"))
                .andExpect(status().isUnauthorized());
    }

    // ========== PATCH /api/auth/nickname ==========

    @Test
    @DisplayName("updateNickname: 정상 요청이면 200과 바뀐 nickname 반환")
    void updateNickname_success() throws Exception {
        // given
        UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");
        given(authService.updateNickname(1L, body))
                .willReturn(new UpdateNicknameResponse("우주탐험가"));

        // when / then
        mockMvc.perform(patch("/api/auth/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("우주탐험가"));
    }

    @Test
    @DisplayName("updateNickname: 1자 닉네임이면 400")
    void updateNickname_tooShort() throws Exception {
        UpdateNicknameRequest body = new UpdateNicknameRequest("가");

        mockMvc.perform(patch("/api/auth/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateNickname: 특수문자 포함이면 400")
    void updateNickname_invalidCharacter() throws Exception {
        UpdateNicknameRequest body = new UpdateNicknameRequest("우주!탐험");

        mockMvc.perform(patch("/api/auth/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updateNickname: 중복 닉네임이면 409")
    void updateNickname_duplicated() throws Exception {
        UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");
        given(authService.updateNickname(1L, body))
                .willThrow(new CustomException(ErrorCode.DUPLICATED_NICKNAME));

        mockMvc.perform(patch("/api/auth/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("loginMember", new LoginMember(1L)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("updateNickname: 인증 정보가 없으면 401")
    void updateNickname_unauthenticated() throws Exception {
        UpdateNicknameRequest body = new UpdateNicknameRequest("우주탐험가");

        mockMvc.perform(patch("/api/auth/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
