package com.jxxerick.expensesplit.service.split;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Picks the strategy for a split type and validates the common rules. */
@Component
public class SplitCalculator {

    private final Map<SplitType, SplitStrategy> strategies = new EnumMap<>(SplitType.class);

    public SplitCalculator(List<SplitStrategy> strategies) {
        strategies.forEach(s -> this.strategies.put(s.type(), s));
    }

    public List<Share> calculate(SplitType type, BigDecimal total, List<SplitEntry> entries) {
        if (entries.isEmpty()) {
            throw new BusinessException("A despesa precisa de pelo menos um participante");
        }
        Set<Long> seen = new HashSet<>();
        for (SplitEntry entry : entries) {
            if (!seen.add(entry.memberId())) {
                throw new BusinessException("Membro %d aparece mais de uma vez na divisão".formatted(entry.memberId()));
            }
        }

        SplitStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new BusinessException("Tipo de divisão não suportado: " + type);
        }
        return strategy.split(total, entries);
    }
}
