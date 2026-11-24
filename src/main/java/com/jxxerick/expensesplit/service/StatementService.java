package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.MemberStatementResponse;
import com.jxxerick.expensesplit.dto.StatementLineResponse;
import com.jxxerick.expensesplit.dto.StatementLineType;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bank-statement view of one member: every movement in date order with a running balance. */
@Service
public class StatementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public StatementService(GroupService groupService, ExpenseRepository expenseRepository,
                            SettlementRepository settlementRepository) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    /** Both dates optional and inclusive; movements before {@code from} become the opening balance. */
    @Transactional(readOnly = true)
    public MemberStatementResponse statement(Long groupId, Long memberId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("A data inicial deve ser anterior ou igual à final");
        }
        Group group = groupService.getGroup(groupId);
        Member member = ExpenseService.memberOf(group, memberId);

        BigDecimal opening = ZERO;
        BigDecimal balance = ZERO;
        Totals totals = new Totals();
        List<StatementLineResponse> lines = new ArrayList<>();
        for (Movement movement : movementsOf(group, memberId)) {
            if (to != null && movement.date().isAfter(to)) {
                break;
            }
            if (from != null && movement.date().isBefore(from)) {
                opening = opening.add(movement.effect());
                balance = opening;
                continue;
            }
            balance = balance.add(movement.effect());
            totals.add(movement);
            lines.add(movement.toLine(balance));
        }
        return new MemberStatementResponse(MemberResponse.from(member), from, to, opening, totals.paid,
                totals.share, totals.sent, totals.received, balance, lines);
    }

    /** Oldest first; on the same day expenses come before settlements, then creation order. */
    private List<Movement> movementsOf(Group group, Long memberId) {
        List<Movement> movements = new ArrayList<>();
        for (Expense expense : expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(group.getId()).reversed()) {
            BigDecimal paid = expense.getPayer().getId().equals(memberId) ? expense.getAmount() : ZERO;
            BigDecimal share = expense.getShares().stream()
                    .filter(s -> s.getMember().getId().equals(memberId))
                    .map(ExpenseShare::getAmount)
                    .reduce(ZERO, BigDecimal::add);
            if (paid.signum() != 0 || share.signum() != 0) {
                movements.add(new Movement(expense.getSpentAt(), StatementLineType.EXPENSE, expense.getId(),
                        expense.getDescription(), paid, share, ZERO, ZERO));
            }
        }
        for (Settlement settlement : settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(group.getId()).reversed()) {
            if (settlement.getFrom().getId().equals(memberId)) {
                movements.add(new Movement(settlement.getPaidAt(), StatementLineType.SETTLEMENT_SENT,
                        settlement.getId(), "Pagamento para " + settlement.getTo().getName(),
                        ZERO, ZERO, settlement.getAmount(), ZERO));
            } else if (settlement.getTo().getId().equals(memberId)) {
                movements.add(new Movement(settlement.getPaidAt(), StatementLineType.SETTLEMENT_RECEIVED,
                        settlement.getId(), "Pagamento de " + settlement.getFrom().getName(),
                        ZERO, ZERO, ZERO, settlement.getAmount()));
            }
        }
        // stable sort keeps expenses ahead of settlements on the same day
        movements.sort(Comparator.comparing(Movement::date));
        return movements;
    }

    private record Movement(LocalDate date, StatementLineType type, Long referenceId, String description,
                            BigDecimal paid, BigDecimal share, BigDecimal sent, BigDecimal received) {

        /** Same rule as the group balance: paid - share + sent - received. */
        BigDecimal effect() {
            return paid.subtract(share).add(sent).subtract(received);
        }

        StatementLineResponse toLine(BigDecimal balance) {
            return new StatementLineResponse(date, type, referenceId, description, paid, share, effect(), balance);
        }
    }

    private static final class Totals {
        private BigDecimal paid = ZERO;
        private BigDecimal share = ZERO;
        private BigDecimal sent = ZERO;
        private BigDecimal received = ZERO;

        private void add(Movement movement) {
            paid = paid.add(movement.paid());
            share = share.add(movement.share());
            sent = sent.add(movement.sent());
            received = received.add(movement.received());
        }
    }
}
