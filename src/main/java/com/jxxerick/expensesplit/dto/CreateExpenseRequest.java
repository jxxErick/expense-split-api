package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.SplitType;
import jakarta.validation.Valid;
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

public record CreateExpenseRequest(
        @NotBlank(message = "informe a descrição")
        @Size(max = 120, message = "máximo de 120 caracteres")
        String description,

        @NotNull(message = "informe o valor")
        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "no máximo 2 casas decimais")
        BigDecimal amount,

        @NotNull(message = "informe quem pagou")
        Long payerId,

        @NotNull(message = "informe o tipo de divisão")
        SplitType splitType,

        ExpenseCategory category,

        @PastOrPresent(message = "não pode estar no futuro")
        LocalDate spentAt,

        @NotEmpty(message = "informe os participantes")
        List<@Valid ShareRequest> shares
) {

    /** Missing category falls back to OTHER. */
    public ExpenseCategory categoryOrDefault() {
        return category != null ? category : ExpenseCategory.OTHER;
    }
}
