package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ExchangeRateRequest(
        @NotNull(message = "informe a cotação")
        @DecimalMin(value = "0.000001", message = "deve ser maior que zero")
        @Digits(integer = 9, fraction = 6, message = "no máximo 6 casas decimais")
        BigDecimal exchangeRate
) {
}
