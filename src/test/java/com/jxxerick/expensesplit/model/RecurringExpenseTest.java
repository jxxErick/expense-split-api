package com.jxxerick.expensesplit.model;

import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringExpenseTest {

    private final Group group = TestData.group(1L, "Casa", "Ana", "Bruno");

    @Test
    void monthlySeriesKeepsTheAnchorDayInsteadOfDrifting() {
        RecurringExpense rent = recurring(RecurrenceFrequency.MONTHLY, LocalDate.of(2027, 1, 31), null);

        assertThat(List.of(rent.advance(), rent.advance(), rent.advance()))
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
        assertThat(rent.getNextRunOn()).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    @Test
    void weeklySeriesAddsSevenDays() {
        RecurringExpense cleaning = recurring(RecurrenceFrequency.WEEKLY, LocalDate.of(2026, 11, 21), null);

        cleaning.advance();

        assertThat(cleaning.getNextRunOn()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(cleaning.isDue(LocalDate.of(2026, 11, 27))).isFalse();
        assertThat(cleaning.isDue(LocalDate.of(2026, 11, 28))).isTrue();
    }

    @Test
    void finishesAfterTheLastOccurrenceBeforeEndDate() {
        RecurringExpense gym = recurring(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 21),
                LocalDate.of(2027, 1, 10));

        gym.advance();
        assertThat(gym.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        gym.advance();

        assertThat(gym.getStatus()).isEqualTo(RecurringStatus.FINISHED);
        assertThat(gym.isDue(LocalDate.of(2027, 12, 31))).isFalse();
    }

    @Test
    void resumingSkipsOccurrencesMissedWhilePaused() {
        RecurringExpense internet = recurring(RecurrenceFrequency.MONTHLY, LocalDate.of(2026, 11, 5), null);
        internet.advance();
        internet.pause();

        assertThat(internet.isDue(LocalDate.of(2027, 3, 1))).isFalse();
        internet.resume(LocalDate.of(2027, 3, 1));

        assertThat(internet.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(internet.getNextRunOn()).isEqualTo(LocalDate.of(2027, 3, 5));
        assertThat(internet.getOccurrences()).isEqualTo(4);
    }

    private RecurringExpense recurring(RecurrenceFrequency frequency, LocalDate startOn, LocalDate endOn) {
        return new RecurringExpense(group, "Aluguel", new BigDecimal("1000.00"), TestData.member(group, 1L),
                SplitType.EQUAL, ExpenseCategory.BILLS, frequency, startOn, endOn,
                List.of(new RecurringShare(1L, null), new RecurringShare(2L, null)));
    }
}
