package com.jxxerick.expensesplit.service.receipt;

import java.math.BigDecimal;
import java.util.List;

/** One line of the bill, shared equally by {@code memberIds}. */
public record ReceiptItem(String description, BigDecimal unitPrice, int quantity, List<Long> memberIds) {

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
    }
}
