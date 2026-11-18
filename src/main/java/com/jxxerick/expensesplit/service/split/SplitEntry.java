package com.jxxerick.expensesplit.service.split;

import java.math.BigDecimal;

/**
 * Input for a split: the member and a value whose meaning depends on the split type
 * (ignored for EQUAL, amount for EXACT, percent for PERCENTAGE, weight for SHARES).
 */
public record SplitEntry(Long memberId, BigDecimal value) {

    public static SplitEntry of(Long memberId) {
        return new SplitEntry(memberId, null);
    }

    public static SplitEntry of(Long memberId, String value) {
        return new SplitEntry(memberId, new BigDecimal(value));
    }
}
