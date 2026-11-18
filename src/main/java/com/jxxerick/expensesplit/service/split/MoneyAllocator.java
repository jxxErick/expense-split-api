package com.jxxerick.expensesplit.service.split;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits a money amount proportionally to weights without losing cents,
 * using the largest remainder method: every part is floored to the cent and
 * the leftover cents go to the parts with the largest fractional remainders.
 */
public final class MoneyAllocator {

    private static final MathContext PRECISION = MathContext.DECIMAL128;

    private MoneyAllocator() {
    }

    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() <= 0) {
            throw new IllegalArgumentException("weights must add up to a positive value");
        }

        long totalCents = total.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        BigDecimal centsDecimal = BigDecimal.valueOf(totalCents);

        long[] cents = new long[weights.size()];
        BigDecimal[] remainders = new BigDecimal[weights.size()];
        long allocated = 0;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal exact = centsDecimal.multiply(weights.get(i)).divide(weightSum, PRECISION);
            BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
            cents[i] = floor.longValueExact();
            remainders[i] = exact.subtract(floor);
            allocated += cents[i];
        }

        // ties keep the original order, so results are deterministic
        List<Integer> byRemainder = IntStream.range(0, weights.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainders[i]).reversed()
                        .thenComparing(i -> i))
                .toList();
        long leftover = totalCents - allocated;
        for (int k = 0; k < leftover; k++) {
            cents[byRemainder.get(k)]++;
        }

        List<BigDecimal> result = new ArrayList<>(cents.length);
        for (long c : cents) {
            result.add(BigDecimal.valueOf(c, 2));
        }
        return result;
    }
}
