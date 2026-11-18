package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.SplitType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private final SplitCalculator calculator = new SplitCalculator(List.of(
            new EqualSplitStrategy(), new ExactSplitStrategy(), new PercentageSplitStrategy(), new SharesSplitStrategy()));

    @Test
    void delegatesToStrategyOfType() {
        List<Share> shares = calculator.calculate(SplitType.SHARES, new BigDecimal("40.00"),
                List.of(SplitEntry.of(1L, "3"), SplitEntry.of(2L, "1")));

        assertThat(shares).containsExactly(new Share(1L, new BigDecimal("30.00")), new Share(2L, new BigDecimal("10.00")));
    }

    @Test
    void rejectsEmptyParticipants() {
        assertThatThrownBy(() -> calculator.calculate(SplitType.EQUAL, BigDecimal.TEN, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsDuplicatedMember() {
        assertThatThrownBy(() -> calculator.calculate(SplitType.EQUAL, BigDecimal.TEN,
                List.of(SplitEntry.of(1L), SplitEntry.of(1L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void rejectsTypeWithoutStrategy() {
        SplitCalculator onlyEqual = new SplitCalculator(List.of(new EqualSplitStrategy()));

        assertThatThrownBy(() -> onlyEqual.calculate(SplitType.EXACT, BigDecimal.TEN, List.of(SplitEntry.of(1L, "10"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EXACT");
    }
}
