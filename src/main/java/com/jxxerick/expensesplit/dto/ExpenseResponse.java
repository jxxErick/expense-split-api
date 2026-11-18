package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        MemberResponse payer,
        SplitType splitType,
        LocalDate spentAt,
        List<ShareResponse> shares
) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                MemberResponse.from(expense.getPayer()),
                expense.getSplitType(),
                expense.getSpentAt(),
                expense.getShares().stream().map(ShareResponse::from).toList());
    }
}
