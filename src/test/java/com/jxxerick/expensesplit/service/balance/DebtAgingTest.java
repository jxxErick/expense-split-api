package com.jxxerick.expensesplit.service.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DebtAgingTest {

    private static final LocalDate AUG_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Test
    void eachNewDebtOpensItsOwnLot() {
        List<DebtAging.Lot> lots = DebtAging.openLots(List.of(entry(AUG_1, "-30.00"), entry(SEP_1, "-20.00")));

        assertThat(lots).containsExactly(lot(AUG_1, "30.00"), lot(SEP_1, "20.00"));
    }

    @Test
    void paymentsSettleTheOldestDebtFirst() {
        List<DebtAging.Lot> lots = DebtAging.openLots(List.of(
                entry(AUG_1, "-30.00"), entry(SEP_1, "-20.00"), entry(OCT_1, "40.00")));

        assertThat(lots).containsExactly(lot(SEP_1, "10.00"));
    }

    @Test
    void creditTheMemberHadAbsorbsLaterDebt() {
        List<DebtAging.Lot> lots = DebtAging.openLots(List.of(
                entry(AUG_1, "50.00"), entry(SEP_1, "-30.00"), entry(OCT_1, "-45.00")));

        assertThat(lots).containsExactly(lot(OCT_1, "25.00"));
    }

    @Test
    void overpaymentLeavesNothingOpenAndBecomesCredit() {
        List<DebtAging.Lot> lots = DebtAging.openLots(List.of(
                entry(AUG_1, "-30.00"), entry(SEP_1, "50.00"), entry(OCT_1, "-15.00")));

        assertThat(lots).isEmpty();
    }

    @Test
    void debtsOnTheSameDayAreMerged() {
        List<DebtAging.Lot> lots = DebtAging.openLots(List.of(entry(AUG_1, "-10.00"), entry(AUG_1, "-5.50")));

        assertThat(lots).containsExactly(lot(AUG_1, "15.50"));
    }

    @Test
    void zeroEffectsAreIgnored() {
        assertThat(DebtAging.openLots(List.of(entry(AUG_1, "0.00")))).isEmpty();
    }

    @Test
    void ageIsCountedInDays() {
        assertThat(lot(AUG_1, "1.00").ageInDays(OCT_1)).isEqualTo(61);
    }

    @ParameterizedTest
    @CsvSource({"0, UP_TO_30_DAYS", "30, UP_TO_30_DAYS", "31, FROM_31_TO_60_DAYS", "60, FROM_31_TO_60_DAYS",
            "61, FROM_61_TO_90_DAYS", "90, FROM_61_TO_90_DAYS", "91, OVER_90_DAYS", "400, OVER_90_DAYS"})
    void bucketsFollowTheUsualRanges(long days, AgingBucket expected) {
        assertThat(AgingBucket.of(days)).isEqualTo(expected);
    }

    private static DebtAging.Entry entry(LocalDate date, String effect) {
        return new DebtAging.Entry(date, new BigDecimal(effect));
    }

    private static DebtAging.Lot lot(LocalDate since, String amount) {
        return new DebtAging.Lot(since, new BigDecimal(amount));
    }
}
