package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public SplitType type() {
        return SplitType.EXACT;
    }

    @Override
    public List<Share> split(BigDecimal total, List<SplitEntry> entries) {
        List<BigDecimal> amounts = SplitValues.requirePositive(entries, "o valor");
        if (amounts.stream().anyMatch(a -> a.stripTrailingZeros().scale() > 2)) {
            throw new BusinessException("Os valores devem ter no máximo 2 casas decimais");
        }

        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) {
            throw new BusinessException("As partes somam R$ %s, mas o total da despesa é R$ %s"
                    .formatted(sum.setScale(2, RoundingMode.UNNECESSARY), total.setScale(2, RoundingMode.UNNECESSARY)));
        }

        return entries.stream()
                .map(e -> new Share(e.memberId(), e.value().setScale(2, RoundingMode.UNNECESSARY)))
                .toList();
    }
}
