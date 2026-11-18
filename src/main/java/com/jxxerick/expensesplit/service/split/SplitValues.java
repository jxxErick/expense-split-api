package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;

final class SplitValues {

    private SplitValues() {
    }

    /** Every entry must carry a positive value; {@code label} names it in the error message. */
    static List<BigDecimal> requirePositive(List<SplitEntry> entries, String label) {
        for (SplitEntry entry : entries) {
            if (entry.value() == null || entry.value().signum() <= 0) {
                throw new BusinessException("Informe %s do membro %d (maior que zero)".formatted(label, entry.memberId()));
            }
        }
        return entries.stream().map(SplitEntry::value).toList();
    }
}
