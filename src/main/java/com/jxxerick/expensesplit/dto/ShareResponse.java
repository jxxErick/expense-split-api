package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseShare;

import java.math.BigDecimal;

public record ShareResponse(Long memberId, String memberName, BigDecimal amount) {

    public static ShareResponse from(ExpenseShare share) {
        return new ShareResponse(share.getMember().getId(), share.getMember().getName(), share.getAmount());
    }
}
