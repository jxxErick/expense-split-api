package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementResponse(Long id, MemberResponse from, MemberResponse to, BigDecimal amount, LocalDate paidAt) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                MemberResponse.from(settlement.getFrom()),
                MemberResponse.from(settlement.getTo()),
                settlement.getAmount(),
                settlement.getPaidAt());
    }
}
