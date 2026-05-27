package com.elipair.spacestudyship.controller.timer;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.timer.dto.*;
import com.elipair.spacestudyship.study.timer.service.TimerSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TimerSessionControllerTest {

    @Mock TimerSessionService service;
    @InjectMocks TimerSessionController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver loginMemberStub = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(LoginMember.class);
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                    org.springframework.web.context.request.NativeWebRequest webRequest,
                                                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new LoginMember(1L);
            }
        };

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .setValidator(validator)
                .setMessageConverters(jsonConverter)
                .build();
    }

    @Test
    @DisplayName("POST /api/timer-sessions — 201, { session, fuelCharged } (90분 → 3연료, 30분=1연료 환산)")
    void create_201() throws Exception {
        TimerSessionResponse sessionRes = new TimerSessionResponse(
                "sess-1", "todo-1", "수학",
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:30:00Z"),
                90);
        given(service.create(eq(1L), any(TimerSessionCreateRequest.class), any()))
                .willReturn(new TimerSessionCreateResponse(sessionRes, 3));

        String body = """
                {
                  "todoId": "todo-1",
                  "todoTitle": "수학",
                  "startedAt": "2026-05-25T01:00:00Z",
                  "endedAt": "2026-05-25T02:30:00Z",
                  "durationMinutes": 90
                }
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session.id").value("sess-1"))
                .andExpect(jsonPath("$.session.durationMinutes").value(90))
                .andExpect(jsonPath("$.fuelCharged").value(3));
    }

    @Test
    @DisplayName("POST: Idempotency-Key 헤더 → 서비스에 전달")
    void create_idempotencyKeyPassThrough() throws Exception {
        TimerSessionResponse sessionRes = new TimerSessionResponse(
                "sess-1", null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                60);
        given(service.create(eq(1L), any(), eq("idem-abc")))
                .willReturn(new TimerSessionCreateResponse(sessionRes, 2));

        String body = """
                {"startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .header("Idempotency-Key", "idem-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(service).create(eq(1L), any(), eq("idem-abc"));
    }

    @Test
    @DisplayName("POST: 비즈니스 검증 실패 (FUTURE_SESSION) → 400 + code")
    void create_futureSession_400() throws Exception {
        willThrow(new CustomException(ErrorCode.FUTURE_SESSION))
                .given(service).create(eq(1L), any(), any());

        String body = """
                {"startedAt":"2030-01-01T00:00:00Z","endedAt":"2030-01-01T01:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_SESSION"));
    }

    @Test
    @DisplayName("POST: NotNull 위반 (durationMinutes 누락) → 400 INVALID_INPUT_VALUE")
    void create_missingField_400() throws Exception {
        String body = """
                {"startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z"}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("POST: 본문 파싱 실패 → 400 INVALID_REQUEST_BODY")
    void create_malformedBody_400() throws Exception {
        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    @DisplayName("POST: TODO_NOT_FOUND → 404")
    void create_todoNotFound_404() throws Exception {
        willThrow(new CustomException(ErrorCode.TODO_NOT_FOUND))
                .given(service).create(eq(1L), any(), any());

        String body = """
                {"todoId":"nope","startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/timer-sessions — 200, Page envelope, 인자 그대로 전달")
    void getList_200() throws Exception {
        given(service.getList(eq(1L), eq("2026-05-20"), eq("2026-05-25"), eq("t-1"), eq(0), eq(20)))
                .willReturn(new TimerSessionListResponse(
                        List.of(new TimerSessionResponse(
                                "sess-1", "t-1", "수학",
                                Instant.parse("2026-05-25T01:00:00Z"),
                                Instant.parse("2026-05-25T02:00:00Z"),
                                60)),
                        0, 20, 1L, 1));

        mockMvc.perform(get("/api/timer-sessions")
                        .param("startDate", "2026-05-20")
                        .param("endDate", "2026-05-25")
                        .param("todoId", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("sess-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET: 잘못된 날짜 포맷 → 400")
    void getList_badDate_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("startDate", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET: size > 100 → 400")
    void getList_sizeOverMax_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET: page < 0 → 400")
    void getList_negativePage_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/timer-sessions/today-stats — 200, 6필드 (today + lifetime + monthly)")
    void todayStats_200() throws Exception {
        given(service.getTodayStats(1L))
                .willReturn(new TodayStatsResponse(180, 3, 7, 12450, 287, 1820));

        mockMvc.perform(get("/api/timer-sessions/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(180))
                .andExpect(jsonPath("$.sessionCount").value(3))
                .andExpect(jsonPath("$.streak").value(7))
                .andExpect(jsonPath("$.lifetimeMinutes").value(12450))
                .andExpect(jsonPath("$.lifetimeSessionCount").value(287))
                .andExpect(jsonPath("$.monthlyMinutes").value(1820));
    }

    @Test
    @DisplayName("GET /api/timer-sessions/today-stats — 0건 회원: 신규 3필드도 0 (null 아님)")
    void todayStats_zero_neverNull() throws Exception {
        given(service.getTodayStats(1L))
                .willReturn(new TodayStatsResponse(0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/timer-sessions/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeMinutes").isNumber())
                .andExpect(jsonPath("$.lifetimeMinutes").value(0))
                .andExpect(jsonPath("$.lifetimeSessionCount").isNumber())
                .andExpect(jsonPath("$.lifetimeSessionCount").value(0))
                .andExpect(jsonPath("$.monthlyMinutes").isNumber())
                .andExpect(jsonPath("$.monthlyMinutes").value(0));
    }
}
