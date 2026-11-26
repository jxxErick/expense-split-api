package com.jxxerick.expensesplit.service.receipt;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.service.split.MoneyAllocator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits a restaurant bill item by item: each item is shared equally by whoever consumed it,
 * then the service charge and the discount are spread in proportion to what each person consumed.
 * Every step goes through {@link MoneyAllocator}, so no cent is lost or created.
 */
public final class ReceiptSplitter {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ReceiptSplitter() {
    }

    /** {@code serviceChargePercent} and {@code discount} may be null, meaning zero. */
    public static ReceiptBill split(List<ReceiptItem> items, BigDecimal serviceChargePercent, BigDecimal discount) {
        if (items.isEmpty()) {
            throw new BusinessException("A comanda precisa de pelo menos um item");
        }

        // insertion order = order each member first shows up on the receipt
        Map<Long, BigDecimal> consumed = new LinkedHashMap<>();
        for (ReceiptItem item : items) {
            ensureNoRepeatedMember(item);
            List<BigDecimal> parts = MoneyAllocator.allocate(item.total(),
                    Collections.nCopies(item.memberIds().size(), BigDecimal.ONE));
            for (int i = 0; i < parts.size(); i++) {
                consumed.merge(item.memberIds().get(i), parts.get(i), BigDecimal::add);
            }
        }

        List<Long> members = List.copyOf(consumed.keySet());
        List<BigDecimal> subtotals = members.stream().map(consumed::get).toList();
        BigDecimal subtotal = sum(subtotals);

        BigDecimal serviceCharge = serviceCharge(subtotal, serviceChargePercent);
        BigDecimal discountValue = discount == null ? BigDecimal.ZERO.setScale(2) : discount.setScale(2, RoundingMode.UNNECESSARY);
        if (discountValue.compareTo(subtotal) > 0) {
            throw new BusinessException("O desconto (R$ %s) não pode passar do consumo (R$ %s)"
                    .formatted(discountValue, subtotal));
        }
        BigDecimal total = subtotal.add(serviceCharge).subtract(discountValue);
        if (total.signum() <= 0) {
            throw new BusinessException("Com esse desconto a comanda fica zerada; não há o que dividir");
        }

        // weights are the subtotals, so a discount never exceeds what someone consumed
        List<BigDecimal> serviceParts = MoneyAllocator.allocate(serviceCharge, subtotals);
        List<BigDecimal> discountParts = MoneyAllocator.allocate(discountValue, subtotals);

        List<MemberBill> bills = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            BigDecimal memberTotal = subtotals.get(i).add(serviceParts.get(i)).subtract(discountParts.get(i));
            bills.add(new MemberBill(members.get(i), subtotals.get(i), serviceParts.get(i), discountParts.get(i), memberTotal));
        }
        return new ReceiptBill(subtotal, serviceCharge, discountValue, total, bills);
    }

    /** Rounded half up to the cent, like the printed bill. */
    static BigDecimal serviceCharge(BigDecimal subtotal, BigDecimal percent) {
        if (percent == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return subtotal.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static void ensureNoRepeatedMember(ReceiptItem item) {
        Set<Long> seen = new HashSet<>();
        for (Long memberId : item.memberIds()) {
            if (!seen.add(memberId)) {
                throw new BusinessException("Membro %d aparece duas vezes no item '%s'".formatted(memberId, item.description()));
            }
        }
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
