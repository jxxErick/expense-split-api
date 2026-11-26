package com.jxxerick.expensesplit.service.receipt;

import java.math.BigDecimal;

/** What one member owes: their consumption plus their part of the service charge minus their part of the discount. */
public record MemberBill(Long memberId, BigDecimal subtotal, BigDecimal serviceCharge, BigDecimal discount,
                         BigDecimal total) {
}
