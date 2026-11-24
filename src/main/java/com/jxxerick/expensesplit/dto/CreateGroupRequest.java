package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "informe o nome do grupo")
        @Size(max = 80, message = "máximo de 80 caracteres")
        String name,

        @NotNull(message = "informe os membros")
        @Size(min = 2, message = "o grupo precisa de pelo menos 2 membros")
        List<@NotBlank(message = "nome do membro vazio") @Size(max = 60) String> members,

        @Pattern(regexp = "[A-Za-z]{3}", message = "use o código ISO de 3 letras (ex.: BRL)")
        String currency
) {

    public CreateGroupRequest(String name, List<String> members) {
        this(name, members, null);
    }

    /** Missing currency falls back to BRL. */
    public String currencyOrDefault() {
        return currency != null ? currency : Group.DEFAULT_CURRENCY;
    }
}
