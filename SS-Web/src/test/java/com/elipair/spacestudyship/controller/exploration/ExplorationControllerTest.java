package com.elipair.spacestudyship.controller.exploration;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.common.exception.InsufficientFuelException;
import com.elipair.spacestudyship.study.exploration.constant.NodeType;
import com.elipair.spacestudyship.study.exploration.dto.PlanetResponse;
import com.elipair.spacestudyship.study.exploration.dto.PlanetUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionResponse;
import com.elipair.spacestudyship.study.exploration.dto.RegionUnlockResponse;
import com.elipair.spacestudyship.study.exploration.dto.UnlockedNodeDto;
import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.service.ExplorationService;
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
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExplorationControllerTest {

    @Mock ExplorationService explorationService;
    @InjectMocks ExplorationController controller;

    MockMvc mockMvc;

    private ExplorationNode planetNode() {
        return ExplorationNode.builder().id("earth").name("지구").nodeType(NodeType.PLANET)
                .depth(2).icon("earth").requiredFuel(0).sortOrder(0)
                .description("시작점").mapX(0.5).mapY(0.08).build();
    }

    private ExplorationNode regionNode() {
        return ExplorationNode.builder().id("korea").name("대한민국").nodeType(NodeType.REGION)
                .depth(3).icon("KR").parentId("earth").requiredFuel(0).sortOrder(0)
                .description("한반도").mapX(0).mapY(0).build();
    }

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .build();
    }

    @Test
    @DisplayName("GET /api/explorations/planets — 200, nodeType 소문자")
    void getPlanets_200() throws Exception {
        given(explorationService.getPlanets(1L)).willReturn(List.of(
                PlanetResponse.of(planetNode(), true, false, 1, 2, 0.5, null)));

        mockMvc.perform(get("/api/explorations/planets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("earth"))
                .andExpect(jsonPath("$[0].nodeType").value("planet"))
                .andExpect(jsonPath("$[0].isUnlocked").value(true))
                .andExpect(jsonPath("$[0].progress.totalChildren").value(2));
    }

    @Test
    @DisplayName("GET /api/explorations/planets/{id}/regions — 200")
    void getRegions_200() throws Exception {
        given(explorationService.getRegions(1L, "earth")).willReturn(List.of(
                RegionResponse.of(regionNode(), true, true, null)));

        mockMvc.perform(get("/api/explorations/planets/earth/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("korea"))
                .andExpect(jsonPath("$[0].nodeType").value("region"));
    }

    @Test
    @DisplayName("GET regions — 행성 없음 404 PLANET_NOT_FOUND")
    void getRegions_404() throws Exception {
        given(explorationService.getRegions(1L, "nope"))
                .willThrow(new CustomException(ErrorCode.PLANET_NOT_FOUND));

        mockMvc.perform(get("/api/explorations/planets/nope/regions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANET_NOT_FOUND"))
                .andExpect(jsonPath("$.requiredFuel").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/explorations/regions/{id}/unlock — 200")
    void unlockRegion_200() throws Exception {
        given(explorationService.unlockRegion(1L, "japan")).willReturn(
                new RegionUnlockResponse(
                        new UnlockedNodeDto("japan", "일본", true, true, "2026-04-16T11:00:00Z"),
                        1, 249, false));

        mockMvc.perform(post("/api/explorations/regions/japan/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region.id").value("japan"))
                .andExpect(jsonPath("$.fuelConsumed").value(1))
                .andExpect(jsonPath("$.currentFuel").value(249))
                .andExpect(jsonPath("$.planetCleared").value(false));
    }

    @Test
    @DisplayName("POST region unlock — 연료 부족 400 + requiredFuel/currentFuel 본문")
    void unlockRegion_insufficientFuel_400() throws Exception {
        willThrow(new InsufficientFuelException(3, 1))
                .given(explorationService).unlockRegion(1L, "usa");

        mockMvc.perform(post("/api/explorations/regions/usa/unlock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUEL"))
                .andExpect(jsonPath("$.requiredFuel").value(3))
                .andExpect(jsonPath("$.currentFuel").value(1));
    }

    @Test
    @DisplayName("POST /api/explorations/planets/{id}/unlock — 200")
    void unlockPlanet_200() throws Exception {
        given(explorationService.unlockPlanet(1L, "mercury")).willReturn(
                new PlanetUnlockResponse(
                        new UnlockedNodeDto("mercury", "수성", true, false, "2026-04-16T11:30:00Z"),
                        3, 97));

        mockMvc.perform(post("/api/explorations/planets/mercury/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planet.id").value("mercury"))
                .andExpect(jsonPath("$.fuelConsumed").value(3))
                .andExpect(jsonPath("$.currentFuel").value(97));
    }

    @Test
    @DisplayName("POST planet unlock — 선행 미클리어 400 PREREQUISITE_NOT_CLEARED")
    void unlockPlanet_prerequisite_400() throws Exception {
        willThrow(new CustomException(ErrorCode.PREREQUISITE_NOT_CLEARED))
                .given(explorationService).unlockPlanet(1L, "mercury");

        mockMvc.perform(post("/api/explorations/planets/mercury/unlock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_CLEARED"));
    }
}
