package com.jxxerick.expensesplit.service.split;

import java.math.BigDecimal;

/** Amount a member owes for an expense. */
public record Share(Long memberId, BigDecimal amount) {
}
