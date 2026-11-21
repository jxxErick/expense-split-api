package com.jxxerick.expensesplit.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetLevelTest {

    @ParameterizedTest
    @CsvSource({
            "0.00, OK",
            "79.99, OK",
            "80.00, WARNING",
            "100.00, WARNING",
            "100.01, EXCEEDED"
    })
    void classifiesUsageAgainstALimitOf100(String spent, BudgetLevel expected) {
        assertThat(BudgetLevel.of(new BigDecimal(spent), new BigDecimal("100.00"))).isEqualTo(expected);
    }
}
