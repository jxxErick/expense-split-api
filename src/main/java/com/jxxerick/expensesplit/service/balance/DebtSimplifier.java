package com.jxxerick.expensesplit.service.balance;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turns net balances into a short list of transfers.
 * <p>
 * Greedy: the largest debtor always pays the largest creditor as much as possible.
 * Each step zeroes at least one member, so the result has at most {@code n - 1} transfers,
 * no matter how many expenses created the balances.
 */
@Component
public class DebtSimplifier {

    private record Position(Long memberId, long cents) {
    }

    private static final Comparator<Position> LARGEST_FIRST =
            Comparator.comparingLong(Position::cents).reversed().thenComparing(Position::memberId);

    /**
     * @param balances net balance per member: positive means the member should receive money,
     *                 negative means the member owes; they must add up to zero
     */
    public List<Transfer> simplify(Map<Long, BigDecimal> balances) {
        PriorityQueue<Position> creditors = new PriorityQueue<>(LARGEST_FIRST);
        PriorityQueue<Position> debtors = new PriorityQueue<>(LARGEST_FIRST);

        long total = 0;
        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            long cents = toCents(entry.getValue());
            total += cents;
            if (cents > 0) {
                creditors.add(new Position(entry.getKey(), cents));
            } else if (cents < 0) {
                debtors.add(new Position(entry.getKey(), -cents));
            }
        }
        if (total != 0) {
            throw new IllegalArgumentException("balances must add up to zero, got " + BigDecimal.valueOf(total, 2));
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty()) {
            Position creditor = creditors.poll();
            Position debtor = debtors.poll();
            long paid = Math.min(creditor.cents(), debtor.cents());

            transfers.add(new Transfer(debtor.memberId(), creditor.memberId(), BigDecimal.valueOf(paid, 2)));

            if (creditor.cents() > paid) {
                creditors.add(new Position(creditor.memberId(), creditor.cents() - paid));
            }
            if (debtor.cents() > paid) {
                debtors.add(new Position(debtor.memberId(), debtor.cents() - paid));
            }
        }
        return transfers;
    }

    private static long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
}
