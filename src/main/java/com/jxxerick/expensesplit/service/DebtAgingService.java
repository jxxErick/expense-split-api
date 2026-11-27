package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.DebtAgingResponse;
import com.jxxerick.expensesplit.dto.DebtAgingResponse.MemberAging;
import com.jxxerick.expensesplit.dto.DebtAgingResponse.OpenDebt;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.balance.AgingBucket;
import com.jxxerick.expensesplit.service.balance.DebtAging;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ages each member's debt: how much of what they owe has been open for 30, 60, 90+ days. */
@Service
public class DebtAgingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final Clock clock;

    public DebtAgingService(GroupService groupService, ExpenseRepository expenseRepository,
                            SettlementRepository settlementRepository, Clock clock) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.clock = clock;
    }

    /** {@code asOf} defaults to today; movements after it are ignored, so past positions can be rebuilt. */
    @Transactional(readOnly = true)
    public DebtAgingResponse aging(Long groupId, LocalDate asOf) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = asOf != null ? asOf : today;
        if (date.isAfter(today)) {
            throw new BusinessException("A data de referência não pode estar no futuro");
        }
        Group group = groupService.getGroup(groupId);
        Map<Long, List<DebtAging.Entry>> entries = entriesByMember(group, date);

        List<MemberAging> members = new ArrayList<>();
        Map<AgingBucket, BigDecimal> totals = emptyBuckets();
        BigDecimal totalOwed = ZERO;
        for (Member member : group.getMembers()) {
            List<DebtAging.Lot> lots = DebtAging.openLots(entries.getOrDefault(member.getId(), List.of()));
            if (lots.isEmpty()) {
                continue;
            }
            MemberAging aging = toMemberAging(member, lots, date);
            aging.buckets().forEach((bucket, amount) -> totals.merge(bucket, amount, BigDecimal::add));
            totalOwed = totalOwed.add(aging.owed());
            members.add(aging);
        }
        members.sort(Comparator.comparing(MemberAging::oldestSince)
                .thenComparing(MemberAging::owed, Comparator.reverseOrder()));
        return new DebtAgingResponse(date, totalOwed, totals, members);
    }

    private static MemberAging toMemberAging(Member member, List<DebtAging.Lot> lots, LocalDate asOf) {
        Map<AgingBucket, BigDecimal> buckets = emptyBuckets();
        BigDecimal owed = ZERO;
        List<OpenDebt> openDebts = new ArrayList<>();
        for (DebtAging.Lot lot : lots) {
            long days = lot.ageInDays(asOf);
            buckets.merge(AgingBucket.of(days), lot.amount(), BigDecimal::add);
            owed = owed.add(lot.amount());
            openDebts.add(new OpenDebt(lot.since(), days, lot.amount()));
        }
        DebtAging.Lot oldest = lots.getFirst();
        return new MemberAging(MemberResponse.from(member), owed, oldest.since(), oldest.ageInDays(asOf), buckets,
                openDebts);
    }

    /** Every bucket present, in order, so the response always has the same shape. */
    private static Map<AgingBucket, BigDecimal> emptyBuckets() {
        Map<AgingBucket, BigDecimal> buckets = new EnumMap<>(AgingBucket.class);
        for (AgingBucket bucket : AgingBucket.values()) {
            buckets.put(bucket, ZERO);
        }
        return buckets;
    }

    /** Same order as the member statement: by date, expenses before settlements, then creation order. */
    private Map<Long, List<DebtAging.Entry>> entriesByMember(Group group, LocalDate asOf) {
        List<Movement> movements = new ArrayList<>();
        for (Expense expense : expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(group.getId()).reversed()) {
            Map<Long, BigDecimal> effects = new LinkedHashMap<>();
            effects.merge(expense.getPayer().getId(), expense.getAmount(), BigDecimal::add);
            for (ExpenseShare share : expense.getShares()) {
                effects.merge(share.getMember().getId(), share.getAmount().negate(), BigDecimal::add);
            }
            movements.add(new Movement(expense.getSpentAt(), effects));
        }
        for (Settlement settlement : settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(group.getId()).reversed()) {
            Map<Long, BigDecimal> effects = new LinkedHashMap<>();
            effects.put(settlement.getFrom().getId(), settlement.getAmount());
            effects.put(settlement.getTo().getId(), settlement.getAmount().negate());
            movements.add(new Movement(settlement.getPaidAt(), effects));
        }
        // stable sort keeps expenses ahead of settlements on the same day
        movements.sort(Comparator.comparing(Movement::date));

        Map<Long, List<DebtAging.Entry>> byMember = new HashMap<>();
        for (Movement movement : movements) {
            if (movement.date().isAfter(asOf)) {
                break;
            }
            movement.effects().forEach((memberId, effect) -> byMember
                    .computeIfAbsent(memberId, id -> new ArrayList<>())
                    .add(new DebtAging.Entry(movement.date(), effect)));
        }
        return byMember;
    }

    private record Movement(LocalDate date, Map<Long, BigDecimal> effects) {
    }
}
