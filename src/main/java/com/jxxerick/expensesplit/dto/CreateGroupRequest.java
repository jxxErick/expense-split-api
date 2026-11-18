package com.jxxerick.expensesplit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "informe o nome do grupo")
        @Size(max = 80, message = "máximo de 80 caracteres")
        String name,

        @NotNull(message = "informe os membros")
        @Size(min = 2, message = "o grupo precisa de pelo menos 2 membros")
        List<@NotBlank(message = "nome do membro vazio") @Size(max = 60) String> members
) {
}
