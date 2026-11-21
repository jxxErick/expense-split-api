package com.jxxerick.expensesplit.service.activity;

import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;

import java.math.BigDecimal;

/** Published by the services whenever something worth showing in the group feed happens. */
public record GroupActivityEvent(Long groupId, ActivityType type, String summary, BigDecimal amount,
                                 Long referenceId) {

    public static GroupActivityEvent groupCreated(Group group) {
        return new GroupActivityEvent(group.getId(), ActivityType.GROUP_CREATED,
                "Grupo '%s' criado com %d membro(s)".formatted(group.getName(), group.getMembers().size()),
                null, group.getId());
    }

    public static GroupActivityEvent memberAdded(Group group, Member member) {
        return new GroupActivityEvent(group.getId(), ActivityType.MEMBER_ADDED,
                "%s entrou no grupo".formatted(member.getName()), null, member.getId());
    }

    public static GroupActivityEvent expenseCreated(Expense expense) {
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_CREATED,
                "%s pagou R$ %s em '%s'".formatted(expense.getPayer().getName(), expense.getAmount(),
                        expense.getDescription()),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent expenseUpdated(Expense expense, BigDecimal previousAmount) {
        String change = previousAmount.compareTo(expense.getAmount()) == 0
                ? "R$ %s".formatted(expense.getAmount())
                : "R$ %s → R$ %s".formatted(previousAmount, expense.getAmount());
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_UPDATED,
                "Despesa '%s' editada (%s)".formatted(expense.getDescription(), change),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent expenseDeleted(Expense expense) {
        return new GroupActivityEvent(expense.getGroup().getId(), ActivityType.EXPENSE_DELETED,
                "Despesa '%s' de R$ %s removida".formatted(expense.getDescription(), expense.getAmount()),
                expense.getAmount(), expense.getId());
    }

    public static GroupActivityEvent settlementRecorded(Settlement settlement) {
        return new GroupActivityEvent(settlement.getGroup().getId(), ActivityType.SETTLEMENT_RECORDED,
                "%s pagou R$ %s para %s".formatted(settlement.getFrom().getName(), settlement.getAmount(),
                        settlement.getTo().getName()),
                settlement.getAmount(), settlement.getId());
    }
}
