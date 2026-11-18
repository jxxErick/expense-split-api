package com.jxxerick.expensesplit.service.balance;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplifierTest {

    private final DebtSimplifier simplifier = new DebtSimplifier();

    @Test
    void largestDebtorPaysLargestCreditorFirst() {
        Map<Long, BigDecimal> balances = balances(1L, "70.00", 2L, "-50.00", 3L, "-20.00");

        assertThat(simplifier.simplify(balances)).containsExactly(
                new Transfer(2L, 1L, new BigDecimal("50.00")),
                new Transfer(3L, 1L, new BigDecimal("20.00")));
    }

    @Test
    void collapsesChainOfDebts() {
        // A owes B 10, B owes C 10 -> nets to A pays C directly
        Map<Long, BigDecimal> balances = balances(1L, "-10.00", 2L, "0.00", 3L, "10.00");

        assertThat(simplifier.simplify(balances)).containsExactly(new Transfer(1L, 3L, new BigDecimal("10.00")));
    }

    @Test
    void returnsNothingWhenEverybodyIsSettled() {
        assertThat(simplifier.simplify(balances(1L, "0", 2L, "0.00"))).isEmpty();
    }

    @Test
    void rejectsBalancesThatDoNotAddUpToZero() {
        assertThatThrownBy(() -> simplifier.simplify(balances(1L, "10.00", 2L, "-9.99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.01");
    }

    @RepeatedTest(25)
    void randomBalancesAreFullySettledWithAtMostNMinusOneTransfers() {
        Random random = new Random();
        int members = 2 + random.nextInt(9);
        Map<Long, BigDecimal> balances = new HashMap<>();
        long sum = 0;
        for (long id = 1; id < members; id++) {
            long cents = random.nextLong(-50_000, 50_000);
            balances.put(id, BigDecimal.valueOf(cents, 2));
            sum += cents;
        }
        balances.put((long) members, BigDecimal.valueOf(-sum, 2));

        List<Transfer> transfers = simplifier.simplify(balances);

        assertThat(transfers.size()).isLessThanOrEqualTo(members - 1);
        assertThat(transfers).allSatisfy(t -> assertThat(t.amount()).isPositive());

        Map<Long, BigDecimal> remaining = new HashMap<>(balances);
        for (Transfer t : transfers) {
            remaining.merge(t.fromMemberId(), t.amount(), BigDecimal::add);
            remaining.merge(t.toMemberId(), t.amount().negate(), BigDecimal::add);
        }
        assertThat(remaining.values()).allSatisfy(v -> assertThat(v).isZero());
    }

    private static Map<Long, BigDecimal> balances(Object... idsAndAmounts) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndAmounts.length; i += 2) {
            map.put((Long) idsAndAmounts[i], new BigDecimal((String) idsAndAmounts[i + 1]));
        }
        return map;
    }
}
