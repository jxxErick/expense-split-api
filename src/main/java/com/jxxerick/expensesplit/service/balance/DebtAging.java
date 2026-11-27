package com.jxxerick.expensesplit.service.balance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Finds out since when a member owes money, the way receivables are aged: every movement that
 * increases the debt opens a lot dated on that day, and every payment (or expense the member paid)
 * settles the oldest lots first (FIFO). What is left open is the current debt, each part with its age.
 */
public final class DebtAging {

    private DebtAging() {
    }

    /** One movement of the member's balance; negative effects increase what they owe. */
    public record Entry(LocalDate date, BigDecimal effect) {
    }

    /** Part of the debt that has been open since {@code since}. */
    public record Lot(LocalDate since, BigDecimal amount) {

        public long ageInDays(LocalDate asOf) {
            return ChronoUnit.DAYS.between(since, asOf);
        }
    }

    /** {@code entries} must be in chronological order; lots come back oldest first. */
    public static List<Lot> openLots(List<Entry> entries) {
        Deque<Lot> lots = new ArrayDeque<>();
        BigDecimal credit = BigDecimal.ZERO;
        for (Entry entry : entries) {
            if (entry.effect().signum() < 0) {
                // a credit the member already had absorbs new debt before it opens a lot
                BigDecimal debt = entry.effect().negate();
                BigDecimal absorbed = credit.min(debt);
                credit = credit.subtract(absorbed);
                open(lots, entry.date(), debt.subtract(absorbed));
            } else if (entry.effect().signum() > 0) {
                credit = credit.add(settleOldestFirst(lots, entry.effect()));
            }
        }
        return List.copyOf(lots);
    }

    private static void open(Deque<Lot> lots, LocalDate date, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return;
        }
        Lot last = lots.peekLast();
        if (last != null && last.since().equals(date)) {
            lots.pollLast();
            amount = amount.add(last.amount());
        }
        lots.addLast(new Lot(date, amount));
    }

    /** Returns whatever is left of the payment once every lot is settled. */
    private static BigDecimal settleOldestFirst(Deque<Lot> lots, BigDecimal payment) {
        while (payment.signum() > 0 && !lots.isEmpty()) {
            Lot oldest = lots.pollFirst();
            if (oldest.amount().compareTo(payment) > 0) {
                lots.addFirst(new Lot(oldest.since(), oldest.amount().subtract(payment)));
                return BigDecimal.ZERO;
            }
            payment = payment.subtract(oldest.amount());
        }
        return payment;
    }
}
