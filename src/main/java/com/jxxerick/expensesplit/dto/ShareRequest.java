package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** {@code value} is ignored for EQUAL, an amount for EXACT, a percent for PERCENTAGE and a weight for SHARES. */
public record ShareRequest(
        @NotNull(message = "informe o membro")
        Long memberId,

        @Positive(message = "deve ser maior que zero")
        BigDecimal value
) {
}
