package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

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
        LocalDate paidAt,

        // currency the amount is in; missing means the group currency
        @Pattern(regexp = "[A-Za-z]{3}", message = "use o código ISO de 3 letras (ex.: USD)")
        String currency,

        // group-currency units per 1 unit of currency; required for a foreign currency
        @DecimalMin(value = "0.000001", message = "deve ser maior que zero")
        @Digits(integer = 9, fraction = 6, message = "no máximo 6 casas decimais")
        BigDecimal exchangeRate
) {

    /** Settlement in the group currency. */
    public CreateSettlementRequest(Long fromMemberId, Long toMemberId, BigDecimal amount, LocalDate paidAt) {
        this(fromMemberId, toMemberId, amount, paidAt, null, null);
    }
}
