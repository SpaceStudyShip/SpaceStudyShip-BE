package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "거래 내역 페이지 응답")
public record FuelTransactionListResponse(
        List<FuelTransactionResponse> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {
    public static FuelTransactionListResponse from(Page<FuelTransaction> page) {
        return new FuelTransactionListResponse(
                page.getContent().stream().map(FuelTransactionResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
