package com.jxxerick.expensesplit.service.balance;

/** Usual receivables ranges, by days since the debt was opened. */
public enum AgingBucket {
    UP_TO_30_DAYS,
    FROM_31_TO_60_DAYS,
    FROM_61_TO_90_DAYS,
    OVER_90_DAYS;

    public static AgingBucket of(long days) {
        if (days <= 30) {
            return UP_TO_30_DAYS;
        }
        if (days <= 60) {
            return FROM_31_TO_60_DAYS;
        }
        return days <= 90 ? FROM_61_TO_90_DAYS : OVER_90_DAYS;
    }
}
