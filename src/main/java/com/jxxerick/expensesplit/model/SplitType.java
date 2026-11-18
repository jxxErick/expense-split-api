package com.jxxerick.expensesplit.model;

public enum SplitType {
    /** Same amount for every participant. */
    EQUAL,
    /** Each participant has a fixed amount; they must add up to the total. */
    EXACT,
    /** Each participant has a percentage; they must add up to 100. */
    PERCENTAGE,
    /** Proportional to weights (e.g. 2 shares vs 1 share). */
    SHARES
}
