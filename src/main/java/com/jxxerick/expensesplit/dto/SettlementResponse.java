package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Settlement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code amount} is in the group currency; {@code foreign} is null for local settlements. */
public record SettlementResponse(Long id, MemberResponse from, MemberResponse to, BigDecimal amount, LocalDate paidAt,
                                 ExpenseResponse.ForeignAmountResponse foreign) {

    public SettlementResponse(Long id, MemberResponse from, MemberResponse to, BigDecimal amount, LocalDate paidAt) {
        this(id, from, to, amount, paidAt, null);
    }

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                MemberResponse.from(settlement.getFrom()),
                MemberResponse.from(settlement.getTo()),
                settlement.getAmount(),
                settlement.getPaidAt(),
                settlement.isForeign() ? new ExpenseResponse.ForeignAmountResponse(settlement.getOriginalCurrency(),
                        settlement.getOriginalAmount(), settlement.getExchangeRate()) : null);
    }
}
