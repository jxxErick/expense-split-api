package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class EqualSplitStrategy extends WeightedSplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EQUAL;
    }

    @Override
    protected List<BigDecimal> weightsOf(List<SplitEntry> entries) {
        return Collections.nCopies(entries.size(), BigDecimal.ONE);
    }
}
