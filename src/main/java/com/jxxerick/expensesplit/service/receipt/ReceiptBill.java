package com.jxxerick.expensesplit.service.receipt;

import java.math.BigDecimal;
import java.util.List;

/** {@code total = subtotal + serviceCharge - discount}, and the members' totals add up to it. */
public record ReceiptBill(BigDecimal subtotal, BigDecimal serviceCharge, BigDecimal discount, BigDecimal total,
                          List<MemberBill> members) {
}
