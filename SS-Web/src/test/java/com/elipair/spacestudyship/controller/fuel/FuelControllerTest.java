package com.elipair.spacestudyship.controller.fuel;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.fuel.constant.TransactionType;
import com.elipair.spacestudyship.study.fuel.dto.FuelResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionListResponse;
import com.elipair.spacestudyship.study.fuel.dto.FuelTransactionResponse;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FuelControllerTest {

    @Mock FuelService fuelService;
    @InjectMocks FuelController fuelController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver loginMemberStub = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(LoginMember.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          org.springframework.web.context.request.NativeWebRequest webRequest,
                                          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new LoginMember(1L);
            }
        };

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(fuelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/fuel — 200, FuelResponse 본문")
    void getFuel_200() throws Exception {
        given(fuelService.getFuel(1L))
                .willReturn(new FuelResponse(350, 1200, 850, 0, "2026-04-16T10:30:00Z"));

        mockMvc.perform(get("/api/fuel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFuel").value(350))
                .andExpect(jsonPath("$.totalCharged").value(1200))
                .andExpect(jsonPath("$.totalConsumed").value(850))
                .andExpect(jsonPath("$.pendingMinutes").value(0))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-04-16T10:30:00Z"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions — 200, Page envelope")
    void getTransactions_200() throws Exception {
        given(fuelService.getTransactions(eq(1L), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .willReturn(new FuelTransactionListResponse(
                        List.of(new FuelTransactionResponse(
                                "tx-1", "charge", 90, "STUDY_SESSION", "s-1", 350, "2026-04-16T10:30:00Z")),
                        0, 20, 1L, 1));

        mockMvc.perform(get("/api/fuel/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("tx-1"))
                .andExpect(jsonPath("$.content[0].type").value("charge"))
                .andExpect(jsonPath("$.content[0].reason").value("STUDY_SESSION"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?type=invalid → 400 INVALID_INPUT_VALUE")
    void getTransactions_invalidType_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?type=invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?startDate=2026-13-01 → 400 (Pattern 위반)")
    void getTransactions_invalidStartDate_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?startDate=2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?size=200 → 400 (Max 100)")
    void getTransactions_sizeOverMax_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?size=200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?page=-1 → 400 (Min 0)")
    void getTransactions_negativePage_400() throws Exception {
        mockMvc.perform(get("/api/fuel/transactions?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET /api/fuel/transactions?type=charge&startDate=2026-04-01&endDate=2026-04-16 - 인자 그대로 서비스로")
    void getTransactions_argsPassThrough() throws Exception {
        given(fuelService.getTransactions(eq(1L), eq(TransactionType.CHARGE),
                eq("2026-04-01"), eq("2026-04-16"), eq(0), eq(20)))
                .willReturn(new FuelTransactionListResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/fuel/transactions")
                        .param("type", "charge")
                        .param("startDate", "2026-04-01")
                        .param("endDate", "2026-04-16"))
                .andExpect(status().isOk());
    }
}
