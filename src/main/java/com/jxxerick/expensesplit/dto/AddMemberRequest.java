package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @NotBlank(message = "informe o nome do membro")
        @Size(max = 60, message = "máximo de 60 caracteres")
        String name
) {
}
