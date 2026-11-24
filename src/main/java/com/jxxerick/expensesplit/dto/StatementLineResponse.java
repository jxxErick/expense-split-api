package com.jxxerick.expensesplit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One movement of a member: {@code effect} is what it did to the balance
 * ({@code paid - share} for expenses, the amount for settlements) and {@code balance} is the running total.
 */
public record StatementLineResponse(
        LocalDate date,
        StatementLineType type,
        Long referenceId,
        String description,
        BigDecimal paid,
        BigDecimal share,
        BigDecimal effect,
        BigDecimal balance
) {
}
