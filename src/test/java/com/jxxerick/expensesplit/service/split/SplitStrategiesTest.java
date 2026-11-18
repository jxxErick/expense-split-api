package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitStrategiesTest {

    private static List<BigDecimal> amounts(List<Share> shares) {
        return shares.stream().map(Share::amount).toList();
    }

    @Nested
    class Equal {

        private final EqualSplitStrategy strategy = new EqualSplitStrategy();

        @Test
        void splitsEvenlyIgnoringValues() {
            List<Share> shares = strategy.split(new BigDecimal("90.00"),
                    List.of(SplitEntry.of(1L, "999"), SplitEntry.of(2L), SplitEntry.of(3L)));

            assertThat(amounts(shares)).containsOnly(new BigDecimal("30.00"));
            assertThat(shares).extracting(Share::memberId).containsExactly(1L, 2L, 3L);
        }
    }

    @Nested
    class Percentage {

        private final PercentageSplitStrategy strategy = new PercentageSplitStrategy();

        @Test
        void splitsByPercent() {
            List<Share> shares = strategy.split(new BigDecimal("200.00"),
                    List.of(SplitEntry.of(1L, "50"), SplitEntry.of(2L, "30"), SplitEntry.of(3L, "20")));

            assertThat(amounts(shares)).containsExactly(
                    new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"));
        }

        @Test
        void acceptsFractionalPercentsAndKeepsTotal() {
            List<Share> shares = strategy.split(new BigDecimal("10.00"),
                    List.of(SplitEntry.of(1L, "33.3"), SplitEntry.of(2L, "33.3"), SplitEntry.of(3L, "33.4")));

            assertThat(amounts(shares).stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10.00");
        }

        @Test
        void rejectsPercentsThatDoNotAddUpTo100() {
            assertThatThrownBy(() -> strategy.split(BigDecimal.TEN, List.of(SplitEntry.of(1L, "60"), SplitEntry.of(2L, "30"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("90%");
        }

        @Test
        void rejectsMissingPercent() {
            assertThatThrownBy(() -> strategy.split(BigDecimal.TEN, List.of(SplitEntry.of(1L, "100"), SplitEntry.of(2L))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("membro 2");
        }
    }

    @Nested
    class Shares {

        private final SharesSplitStrategy strategy = new SharesSplitStrategy();

        @Test
        void splitsProportionallyToWeights() {
            // a couple (2 shares) and a single person (1 share)
            List<Share> shares = strategy.split(new BigDecimal("300.00"),
                    List.of(SplitEntry.of(1L, "2"), SplitEntry.of(2L, "1")));

            assertThat(amounts(shares)).containsExactly(new BigDecimal("200.00"), new BigDecimal("100.00"));
        }
    }

    @Nested
    class Exact {

        private final ExactSplitStrategy strategy = new ExactSplitStrategy();

        @Test
        void keepsGivenAmounts() {
            List<Share> shares = strategy.split(new BigDecimal("50.00"),
                    List.of(SplitEntry.of(1L, "12.5"), SplitEntry.of(2L, "37.50")));

            assertThat(amounts(shares)).containsExactly(new BigDecimal("12.50"), new BigDecimal("37.50"));
        }

        @Test
        void rejectsAmountsThatDoNotMatchTotal() {
            assertThatThrownBy(() -> strategy.split(new BigDecimal("50.00"),
                    List.of(SplitEntry.of(1L, "10"), SplitEntry.of(2L, "30"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("As partes somam R$ 40.00, mas o total da despesa é R$ 50.00");
        }

        @Test
        void rejectsFractionsOfCents() {
            assertThatThrownBy(() -> strategy.split(new BigDecimal("1.00"),
                    List.of(SplitEntry.of(1L, "0.333"), SplitEntry.of(2L, "0.667"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("2 casas");
        }
    }
}
