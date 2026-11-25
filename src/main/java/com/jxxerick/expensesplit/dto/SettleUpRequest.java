package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/** Body is optional; without {@code paidAt} the settlements are dated today. */
public record SettleUpRequest(
        @PastOrPresent(message = "não pode estar no futuro")
        LocalDate paidAt
) {
}
