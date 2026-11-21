package com.jxxerick.expensesplit.model;

import java.math.BigDecimal;

public enum BudgetLevel {
    OK,
    /** 80% or more of the limit used. */
    WARNING,
    /** Spent more than the limit. */
    EXCEEDED;

    private static final BigDecimal WARNING_RATIO = new BigDecimal("0.80");

    public static BudgetLevel of(BigDecimal spent, BigDecimal limit) {
        if (spent.compareTo(limit) > 0) {
            return EXCEEDED;
        }
        return spent.compareTo(limit.multiply(WARNING_RATIO)) >= 0 ? WARNING : OK;
    }
}
