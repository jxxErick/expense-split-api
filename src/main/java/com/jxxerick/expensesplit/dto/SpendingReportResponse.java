package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

/** Group spending in a period: totals per month and category, plus how much each member consumed. */
public record SpendingReportResponse(
        BigDecimal total,
        int expenseCount,
        BigDecimal averageExpense,
        List<MonthlyTotal> months,
        List<CategoryTotal> categories,
        List<MemberSpending> members
) {

    public record MonthlyTotal(String month, BigDecimal total, int expenseCount) {
    }

    /** Sorted by total, highest first. */
    public record CategoryTotal(ExpenseCategory category, BigDecimal total, int expenseCount, BigDecimal percentage) {
    }

    /** {@code consumed}: sum of the member's shares; {@code percentage}: share of the period total. */
    public record MemberSpending(Long memberId, String memberName, BigDecimal paid, BigDecimal consumed,
                                 BigDecimal percentage) {
    }
}
