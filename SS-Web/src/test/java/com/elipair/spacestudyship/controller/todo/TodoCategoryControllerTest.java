package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.todo.dto.CategoryResponse;
import com.elipair.spacestudyship.study.todo.service.TodoCategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TodoCategoryControllerTest {

    @Mock TodoCategoryService categoryService;
    @InjectMocks TodoCategoryController categoryController;

    MockMvc mockMvc;
    ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver stub = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return p.getParameterType().equals(LoginMember.class);
            }
            @Override public Object resolveArgument(MethodParameter p,
                    ModelAndViewContainer m,
                    org.springframework.web.context.request.NativeWebRequest w,
                    org.springframework.web.bind.support.WebDataBinderFactory f) {
                return new LoginMember(1L);
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(stub)
                .build();
    }

    @Test
    @DisplayName("GET /api/todo-categories — 200")
    void findAll() throws Exception {
        when(categoryService.findAll(1L)).thenReturn(List.of(
                new CategoryResponse("c1", "수학", "math", 0.3, 0.5,
                        "2026-05-23T00:00:00Z", null)));

        mockMvc.perform(get("/api/todo-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("c1"));
    }

    @Test
    @DisplayName("POST /api/todo-categories — 201")
    void create() throws Exception {
        when(categoryService.create(eq(1L), any()))
                .thenReturn(new CategoryResponse("c1", "수학", null, null, null,
                        "2026-05-23T00:00:00Z", null));

        String body = "{\"id\":\"c1\",\"name\":\"수학\"}";

        mockMvc.perform(post("/api/todo-categories")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("c1"));
    }

    @Test
    @DisplayName("PATCH /api/todo-categories/{id} — 404 CATEGORY_NOT_FOUND")
    void update_notFound() throws Exception {
        when(categoryService.update(eq(1L), eq("missing"), any()))
                .thenThrow(new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(patch("/api/todo-categories/missing")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/todo-categories/{id} — 204")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/todo-categories/c1"))
                .andExpect(status().isNoContent());
    }
}
