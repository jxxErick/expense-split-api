package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code amount} and shares are always in the group currency; {@code foreign} is null for local expenses. */
public record ExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        MemberResponse payer,
        SplitType splitType,
        ExpenseCategory category,
        LocalDate spentAt,
        List<ShareResponse> shares,
        ForeignAmountResponse foreign
) {

    public ExpenseResponse(Long id, String description, BigDecimal amount, MemberResponse payer, SplitType splitType,
                           ExpenseCategory category, LocalDate spentAt, List<ShareResponse> shares) {
        this(id, description, amount, payer, splitType, category, spentAt, shares, null);
    }

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                MemberResponse.from(expense.getPayer()),
                expense.getSplitType(),
                expense.getCategory(),
                expense.getSpentAt(),
                expense.getShares().stream().map(ShareResponse::from).toList(),
                expense.isForeign() ? new ForeignAmountResponse(expense.getOriginalCurrency(),
                        expense.getOriginalAmount(), expense.getExchangeRate()) : null);
    }

    /** What was actually paid, before conversion. */
    public record ForeignAmountResponse(String currency, BigDecimal amount, BigDecimal exchangeRate) {
    }
}
