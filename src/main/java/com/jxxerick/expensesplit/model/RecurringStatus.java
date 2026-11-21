package com.jxxerick.expensesplit.model;

public enum RecurringStatus {
    ACTIVE,
    PAUSED,
    /** Reached its end date; never generates again. */
    FINISHED
}
