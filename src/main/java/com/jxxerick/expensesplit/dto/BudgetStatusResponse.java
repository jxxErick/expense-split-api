package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.BudgetLevel;
import com.jxxerick.expensesplit.model.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

/** Budget usage in one month, with a linear forecast while the month is running. */
public record BudgetStatusResponse(
        String month,
        int daysElapsed,
        int daysInMonth,
        List<Item> budgets
) {

    /**
     * {@code projectedSpend} extrapolates the daily average to the whole month and
     * {@code dailyAllowance} is what can still be spent per remaining day (null outside the current month).
     */
    public record Item(
            String scope,
            ExpenseCategory category,
            BigDecimal monthlyLimit,
            BigDecimal spent,
            BigDecimal remaining,
            BigDecimal percentUsed,
            BudgetLevel level,
            BigDecimal projectedSpend,
            BudgetLevel projectedLevel,
            BigDecimal dailyAllowance
    ) {
    }
}
