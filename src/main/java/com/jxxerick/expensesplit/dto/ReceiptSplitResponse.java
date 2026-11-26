package com.jxxerick.expensesplit.dto;

import java.math.BigDecimal;
import java.util.List;

/** Preview of a receipt split: how much each member owes and why. */
public record ReceiptSplitResponse(
        BigDecimal subtotal,
        BigDecimal serviceCharge,
        BigDecimal discount,
        BigDecimal total,
        List<MemberBillResponse> members
) {

    public record MemberBillResponse(Long memberId, String name, BigDecimal subtotal, BigDecimal serviceCharge,
                                     BigDecimal discount, BigDecimal total) {
    }
}
