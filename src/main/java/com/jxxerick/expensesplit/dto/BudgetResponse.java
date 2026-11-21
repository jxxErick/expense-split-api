package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Budget;
import com.jxxerick.expensesplit.model.ExpenseCategory;

import java.math.BigDecimal;

/** {@code scope} is the category name or TOTAL for the whole group. */
public record BudgetResponse(Long id, String scope, ExpenseCategory category, BigDecimal monthlyLimit) {

    public static final String TOTAL_SCOPE = "TOTAL";

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getId(), scopeOf(budget), budget.getCategory(), budget.getMonthlyLimit());
    }

    public static String scopeOf(Budget budget) {
        return budget.isTotal() ? TOTAL_SCOPE : budget.getCategory().name();
    }
}
