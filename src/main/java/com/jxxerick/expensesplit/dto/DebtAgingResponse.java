package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.service.balance.AgingBucket;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Who owes the group and for how long, as of {@code asOf}; members are listed oldest debt first. */
public record DebtAgingResponse(
        LocalDate asOf,
        BigDecimal totalOwed,
        Map<AgingBucket, BigDecimal> buckets,
        List<MemberAging> members
) {

    public record MemberAging(
            MemberResponse member,
            BigDecimal owed,
            LocalDate oldestSince,
            long oldestDays,
            Map<AgingBucket, BigDecimal> buckets,
            List<OpenDebt> openDebts
    ) {
    }

    public record OpenDebt(LocalDate since, long days, BigDecimal amount) {
    }
}
