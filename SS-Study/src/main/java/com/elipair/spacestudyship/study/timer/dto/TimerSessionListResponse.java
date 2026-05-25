package com.elipair.spacestudyship.study.timer.dto;

import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import org.springframework.data.domain.Page;

import java.util.List;

public record TimerSessionListResponse(
        List<TimerSessionResponse> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {
    public static TimerSessionListResponse from(Page<TimerSession> page) {
        return new TimerSessionListResponse(
                page.getContent().stream().map(TimerSessionResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
