package com.jxxerick.expensesplit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Movements of a member in a period; {@code closingBalance = openingBalance + sum(effect)}. */
public record MemberStatementResponse(
        MemberResponse member,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal totalPaid,
        BigDecimal totalShare,
        BigDecimal totalSent,
        BigDecimal totalReceived,
        BigDecimal closingBalance,
        List<StatementLineResponse> lines
) {
}
