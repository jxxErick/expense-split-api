package com.jxxerick.expensesplit.service.balance;

import java.math.BigDecimal;

/** Payment that settles (part of) a debt: {@code from} pays {@code amount} to {@code to}. */
public record Transfer(Long fromMemberId, Long toMemberId, BigDecimal amount) {
}
