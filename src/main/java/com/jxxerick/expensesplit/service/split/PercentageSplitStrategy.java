package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PercentageSplitStrategy extends WeightedSplitStrategy {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public SplitType type() {
        return SplitType.PERCENTAGE;
    }

    @Override
    protected List<BigDecimal> weightsOf(List<SplitEntry> entries) {
        List<BigDecimal> percents = SplitValues.requirePositive(entries, "a porcentagem");
        BigDecimal sum = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BusinessException("As porcentagens somam %s%%, mas devem somar 100%%"
                    .formatted(sum.stripTrailingZeros().toPlainString()));
        }
        return percents;
    }
}
