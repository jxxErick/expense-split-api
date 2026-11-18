package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSettlementRequest(
        @NotNull(message = "informe quem pagou")
        Long fromMemberId,

        @NotNull(message = "informe quem recebeu")
        Long toMemberId,

        @NotNull(message = "informe o valor")
        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal amount,

        @PastOrPresent(message = "não pode estar no futuro")
        LocalDate paidAt
) {
}
