package com.jxxerick.expensesplit.service.split;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyAllocatorTest {

    @Test
    void givesLeftoverCentToFirstPartOnTies() {
        List<BigDecimal> parts = MoneyAllocator.allocate(new BigDecimal("100.00"), Collections.nCopies(3, BigDecimal.ONE));

        assertThat(parts).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    }

    @Test
    void givesLeftoverCentsToLargestRemainders() {
        // exact: 3.3333, 6.6666 -> floors 3.33 / 6.66, the leftover cent goes to the second part
        List<BigDecimal> parts = MoneyAllocator.allocate(new BigDecimal("10.00"), weights("1", "2"));

        assertThat(parts).containsExactly(new BigDecimal("3.33"), new BigDecimal("6.67"));
    }

    @ParameterizedTest
    @CsvSource({"0.01, 3", "0.10, 7", "1234.57, 9", "99999.99, 13"})
    void neverLosesOrCreatesCents(String total, int participants) {
        List<BigDecimal> parts = MoneyAllocator.allocate(new BigDecimal(total), Collections.nCopies(participants, BigDecimal.ONE));

        BigDecimal sum = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(total);
        BigDecimal max = parts.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal min = parts.stream().min(BigDecimal::compareTo).orElseThrow();
        assertThat(max.subtract(min)).isLessThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void rejectsZeroWeights() {
        assertThatThrownBy(() -> MoneyAllocator.allocate(BigDecimal.TEN, weights("0", "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<BigDecimal> weights(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
