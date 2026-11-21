package com.jxxerick.expensesplit.model;

import java.time.LocalDate;

public enum RecurrenceFrequency {
    WEEKLY,
    MONTHLY;

    /**
     * Date of the n-th occurrence (0 = start). Always computed from the start, so a
     * monthly series starting on the 31st goes 31/01, 28/02, 31/03 instead of drifting.
     */
    public LocalDate occurrence(LocalDate start, int n) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
        };
    }
}
