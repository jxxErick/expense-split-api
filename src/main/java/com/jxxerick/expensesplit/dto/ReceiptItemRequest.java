package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Missing quantity means one unit. */
public record ReceiptItemRequest(
        @NotBlank(message = "informe o item")
        @Size(max = 80, message = "máximo de 80 caracteres")
        String description,

        @NotNull(message = "informe o preço")
        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal unitPrice,

        @Min(value = 1, message = "mínimo de 1")
        @Max(value = 99, message = "máximo de 99")
        Integer quantity,

        @NotEmpty(message = "informe quem consumiu")
        List<@NotNull(message = "informe o membro") Long> memberIds
) {

    public int quantityOrDefault() {
        return quantity != null ? quantity : 1;
    }
}
