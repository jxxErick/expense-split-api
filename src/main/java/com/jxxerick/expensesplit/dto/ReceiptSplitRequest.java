package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A restaurant bill in the group currency; it becomes an EXACT expense. */
public record ReceiptSplitRequest(
        @NotBlank(message = "informe a descrição")
        @Size(max = 120, message = "máximo de 120 caracteres")
        String description,

        @NotNull(message = "informe quem pagou")
        Long payerId,

        ExpenseCategory category,

        @PastOrPresent(message = "não pode estar no futuro")
        LocalDate spentAt,

        @NotEmpty(message = "informe os itens")
        @Size(max = 100, message = "máximo de 100 itens")
        List<@Valid ReceiptItemRequest> items,

        // e.g. 10 for the usual 10% tip
        @DecimalMin(value = "0", message = "não pode ser negativa")
        @DecimalMax(value = "30", message = "máximo de 30%")
        @Digits(integer = 2, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal serviceChargePercent,

        @DecimalMin(value = "0", message = "não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal discount
) {

    /** Receipts are usually meals, so a missing category means FOOD. */
    public ExpenseCategory categoryOrDefault() {
        return category != null ? category : ExpenseCategory.FOOD;
    }
}
