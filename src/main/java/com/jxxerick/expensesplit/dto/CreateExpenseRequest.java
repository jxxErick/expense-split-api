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
import jakarta.validation.constraints.Pattern;
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
        List<@Valid ShareRequest> shares,

        // currency the amount and EXACT values are in; missing means the group currency
        @Pattern(regexp = "[A-Za-z]{3}", message = "use o código ISO de 3 letras (ex.: USD)")
        String currency,

        // group-currency units per 1 unit of currency; required for a foreign currency
        @DecimalMin(value = "0.000001", message = "deve ser maior que zero")
        @Digits(integer = 9, fraction = 6, message = "no máximo 6 casas decimais")
        BigDecimal exchangeRate
) {

    /** Expense in the group currency. */
    public CreateExpenseRequest(String description, BigDecimal amount, Long payerId, SplitType splitType,
                                ExpenseCategory category, LocalDate spentAt, List<ShareRequest> shares) {
        this(description, amount, payerId, splitType, category, spentAt, shares, null, null);
    }

    /** Missing category falls back to OTHER. */
    public ExpenseCategory categoryOrDefault() {
        return category != null ? category : ExpenseCategory.OTHER;
    }
}
