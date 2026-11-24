package com.jxxerick.expensesplit.service.activity;

import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/** Published by the services whenever something worth showing in the group feed happens. */
public record GroupActivityEvent(Long groupId, ActivityType type, String summary, BigDecimal amount,
                                 Long referenceId) {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    public static GroupActivityEvent groupCreated(Group group) {
        return new GroupActivityEvent(group.getId(), ActivityType.GROUP_CREATED,
                "Grupo '%s' criado com %d membro(s)".formatted(group.getName(), group.getMembers().size()),
                null, group.getId());
    }

    public static GroupActivityEvent memberAdded(Group group, Member member) {
        return new GroupActivityEvent(group.getId(), ActivityType.MEMBER_ADDED,
                "%s entrou no grupo".formatted(member.getName()), null, member.getId());
    }

    /** A foreign expense shows both values: "Ana pagou US$ 100.00 (R$ 540.00) em 'Hotel'". */
    public static GroupActivityEvent expenseCreated(Expense expense) {
        String paid = expense.isForeign()
                ? "%s (%s)".formatted(money(expense.getOriginalCurrency(), expense.getOriginalAmount()), money(expense))
                : money(expense);
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_CREATED,
                "%s pagou %s em '%s'".formatted(expense.getPayer().getName(), paid, expense.getDescription()),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent expenseUpdated(Expense expense, BigDecimal previousAmount) {
        String currency = expense.getGroup().getCurrency();
        String change = previousAmount.compareTo(expense.getAmount()) == 0
                ? money(expense)
                : "%s → %s".formatted(money(currency, previousAmount), money(expense));
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_UPDATED,
                "Despesa '%s' editada (%s)".formatted(expense.getDescription(), change),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent expenseDeleted(Expense expense) {
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_DELETED,
                "Despesa '%s' de %s removida".formatted(expense.getDescription(), money(expense)),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent settlementRecorded(Settlement settlement) {
        return new GroupActivityEvent(settlement.getGroup().getId(), ActivityType.SETTLEMENT_RECORDED,
                "%s pagou %s para %s".formatted(settlement.getFrom().getName(),
                        money(settlement.getGroup().getCurrency(), settlement.getAmount()),
                        settlement.getTo().getName()),
                settlement.getAmount(), settlement.getId());
    }

    private static String money(Expense expense) {
        return money(expense.getGroup().getCurrency(), expense.getAmount());
    }

    /** Symbol as written in Brazil: R$, US$, € ... */
    static String money(String currency, BigDecimal amount) {
        return Currency.getInstance(currency).getSymbol(PT_BR) + " " + amount;
    }
}
