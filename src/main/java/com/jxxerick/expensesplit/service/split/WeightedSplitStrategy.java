package com.jxxerick.expensesplit.service.split;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Base for strategies that turn each entry into a weight and allocate proportionally. */
abstract class WeightedSplitStrategy implements SplitStrategy {

    @Override
    public final List<Share> split(BigDecimal total, List<SplitEntry> entries) {
        List<BigDecimal> weights = weightsOf(entries);
        List<BigDecimal> amounts = MoneyAllocator.allocate(total, weights);

        List<Share> shares = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            shares.add(new Share(entries.get(i).memberId(), amounts.get(i)));
        }
        return shares;
    }

    protected abstract List<BigDecimal> weightsOf(List<SplitEntry> entries);
}
