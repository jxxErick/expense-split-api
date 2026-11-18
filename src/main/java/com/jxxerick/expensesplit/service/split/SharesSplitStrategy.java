package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SharesSplitStrategy extends WeightedSplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.SHARES;
    }

    @Override
    protected List<BigDecimal> weightsOf(List<SplitEntry> entries) {
        return SplitValues.requirePositive(entries, "as cotas");
    }
}
