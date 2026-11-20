package com.jxxerick.expensesplit.support;

import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Builds entities with ids, as if they were already persisted. */
public final class TestData {

    private TestData() {
    }

    public static Group group(Long id, String name, String... memberNames) {
        Group group = new Group(name);
        ReflectionTestUtils.setField(group, "id", id);
        long memberId = 1;
        for (String memberName : memberNames) {
            Member member = group.addMember(memberName);
            ReflectionTestUtils.setField(member, "id", memberId++);
        }
        return group;
    }

    public static Member member(Group group, Long id) {
        return group.findMember(id).orElseThrow();
    }

    /** Expense paid by {@code payerId}; {@code owed} alternates member id and amount: 1L, "10.00", 2L, "5.00". */
    public static Expense expense(Group group, Long payerId, String amount, Object... owed) {
        Expense expense = new Expense(group, "Despesa", new BigDecimal(amount), member(group, payerId),
                SplitType.EXACT, ExpenseCategory.OTHER, LocalDate.of(2026, 11, 17));
        for (int i = 0; i < owed.length; i += 2) {
            expense.addShare(member(group, (Long) owed[i]), new BigDecimal((String) owed[i + 1]));
        }
        return expense;
    }

    public static Settlement settlement(Group group, Long fromId, Long toId, String amount) {
        return new Settlement(group, member(group, fromId), member(group, toId), new BigDecimal(amount),
                LocalDate.of(2026, 11, 17));
    }
}
