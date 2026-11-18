package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.BalanceResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.TransferResponse;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.balance.DebtSimplifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final DebtSimplifier debtSimplifier;

    public BalanceService(GroupService groupService, ExpenseRepository expenseRepository,
                          DebtSimplifier debtSimplifier) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.debtSimplifier = debtSimplifier;
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> balances(Long groupId) {
        Group group = groupService.getGroup(groupId);
        return ledgerOf(group).values().stream().map(Ledger::toResponse).toList();
    }

    /** Fewest transfers that leave every balance at zero. */
    @Transactional(readOnly = true)
    public List<TransferResponse> simplifiedDebts(Long groupId) {
        Group group = groupService.getGroup(groupId);
        Map<Long, BigDecimal> net = ledgerOf(group).values().stream()
                .collect(Collectors.toMap(l -> l.member.getId(), Ledger::balance));

        Map<Long, MemberResponse> members = group.getMembers().stream()
                .collect(Collectors.toMap(Member::getId, MemberResponse::from));
        return debtSimplifier.simplify(net).stream()
                .map(t -> new TransferResponse(members.get(t.fromMemberId()), members.get(t.toMemberId()), t.amount()))
                .toList();
    }

    private Map<Long, Ledger> ledgerOf(Group group) {
        Map<Long, Ledger> ledgers = group.getMembers().stream()
                .collect(Collectors.toMap(Member::getId, Ledger::new, (a, b) -> a, LinkedHashMap::new));
        for (Expense expense : expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(group.getId())) {
            ledgers.get(expense.getPayer().getId()).addPaid(expense.getAmount());
            for (ExpenseShare share : expense.getShares()) {
                ledgers.get(share.getMember().getId()).addOwed(share.getAmount());
            }
        }
        return ledgers;
    }

    /** Running totals of one member. */
    private static final class Ledger {
        private final Member member;
        private BigDecimal paid = BigDecimal.ZERO.setScale(2);
        private BigDecimal owed = BigDecimal.ZERO.setScale(2);

        private Ledger(Member member) {
            this.member = member;
        }

        private void addPaid(BigDecimal amount) {
            paid = paid.add(amount);
        }

        private void addOwed(BigDecimal amount) {
            owed = owed.add(amount);
        }

        private BigDecimal balance() {
            return paid.subtract(owed);
        }

        private BalanceResponse toResponse() {
            return new BalanceResponse(member.getId(), member.getName(), paid, owed, balance());
        }
    }
}
