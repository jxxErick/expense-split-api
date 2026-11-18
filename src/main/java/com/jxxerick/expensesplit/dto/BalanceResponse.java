package com.jxxerick.expensesplit.dto;

import java.math.BigDecimal;

/**
 * Member position in the group: {@code balance = paid - owed + sent - received}.
 * {@code balance > 0}: the group owes the member; {@code balance < 0}: the member owes the group.
 */
public record BalanceResponse(
        Long memberId,
        String memberName,
        BigDecimal paid,
        BigDecimal owed,
        BigDecimal sent,
        BigDecimal received,
        BigDecimal balance
) {
}
