package com.elipair.spacestudyship.controller.todo;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.todo.dto.TodoResponse;
import com.elipair.spacestudyship.study.todo.service.TodoService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TodoControllerTest {

    @Mock TodoService todoService;
    @InjectMocks TodoController todoController;

    MockMvc mockMvc;
    ObjectMapper om = new ObjectMapper();

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
        mockMvc = MockMvcBuilders.standaloneSetup(todoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .build();
    }

    @Test
    @DisplayName("GET /api/todos — 200")
    void findAll() throws Exception {
        when(todoService.findAll(eq(1L), eq(null), eq(null)))
                .thenReturn(List.of(new TodoResponse("t1", "수학",
                        List.of(), List.of(), List.of(), null, null,
                        "2026-05-23T00:00:00Z", "2026-05-23T00:00:00Z")));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"));
    }

    @Test
    @DisplayName("POST /api/todos — 201")
    void create() throws Exception {
        when(todoService.create(eq(1L), any()))
                .thenReturn(new TodoResponse("t1", "수학",
                        List.of(), List.of(), List.of(), null, null,
                        "2026-05-23T00:00:00Z", "2026-05-23T00:00:00Z"));

        String body = """
                {"id":"t1","title":"수학","categoryIds":[],"scheduledDates":[]}
                """;

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("t1"));
    }

    @Test
    @DisplayName("PATCH /api/todos/{id} — 404 TODO_NOT_FOUND")
    void update_notFound() throws Exception {
        when(todoService.update(eq(1L), eq("missing"), any()))
                .thenThrow(new CustomException(ErrorCode.TODO_NOT_FOUND));

        mockMvc.perform(patch("/api/todos/missing")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/todos/{id} — 204")
    void delete_success() throws Exception {
        mockMvc.perform(delete("/api/todos/t1"))
                .andExpect(status().isNoContent());
    }
}
