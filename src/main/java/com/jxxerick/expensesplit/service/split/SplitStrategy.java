package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.model.SplitType;

import java.math.BigDecimal;
import java.util.List;

/** One way of dividing an expense among participants (Strategy pattern). */
public interface SplitStrategy {

    SplitType type();

    /** Returns one share per entry, in the same order, adding up exactly to {@code total}. */
    List<Share> split(BigDecimal total, List<SplitEntry> entries);
}
