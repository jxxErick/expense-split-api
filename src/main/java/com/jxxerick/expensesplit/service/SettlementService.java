package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateSettlementRequest;
import com.jxxerick.expensesplit.dto.SettlementResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.service.balance.DebtSimplifier;
import com.jxxerick.expensesplit.service.balance.Transfer;
import com.jxxerick.expensesplit.service.currency.CurrencyConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupService groupService;
    private final BalanceService balanceService;
    private final DebtSimplifier debtSimplifier;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public SettlementService(SettlementRepository settlementRepository, GroupService groupService,
                             BalanceService balanceService, DebtSimplifier debtSimplifier, Clock clock,
                             ApplicationEventPublisher events) {
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.debtSimplifier = debtSimplifier;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Records a payment between two members. The payer must owe money and the
     * receiver must be owed money, and the amount can't exceed either debt,
     * so a settlement never flips anyone's position.
     */
    @Transactional
    public SettlementResponse create(Long groupId, CreateSettlementRequest request) {
        Group group = groupService.getGroup(groupId);
        Member from = memberOf(group, request.fromMemberId());
        Member to = memberOf(group, request.toMemberId());
        if (from.getId().equals(to.getId())) {
            throw new BusinessException("Quem paga e quem recebe devem ser membros diferentes");
        }

        Map<Long, BigDecimal> balances = balanceService.netBalances(group);
        BigDecimal debt = balances.get(from.getId()).negate();
        BigDecimal credit = balances.get(to.getId());
        if (debt.signum() <= 0) {
            throw new BusinessException("%s não tem dívidas no grupo".formatted(from.getName()));
        }
        if (credit.signum() <= 0) {
            throw new BusinessException("%s não tem valores a receber no grupo".formatted(to.getName()));
        }
        BigDecimal limit = debt.min(credit);
        String currency = request.currency() == null ? group.getCurrency()
                : CurrencyConverter.normalize(request.currency());
        boolean foreign = !currency.equals(group.getCurrency());
        BigDecimal amount = foreign
                ? convertedAmount(group, currency, request, limit)
                : localAmount(group, request, limit);

        LocalDate paidAt = request.paidAt() != null ? request.paidAt() : LocalDate.now(clock);
        Settlement settlement = new Settlement(group, from, to, amount, paidAt);
        if (foreign) {
            settlement.paidInForeignCurrency(currency, request.amount(), request.exchangeRate());
        }
        Settlement saved = settlementRepository.save(settlement);
        events.publishEvent(GroupActivityEvent.settlementRecorded(saved));
        return SettlementResponse.from(saved);
    }

    /**
     * Records every transfer of the simplified plan at once, leaving all balances at zero.
     * Runs in one transaction: either the whole group is settled or nothing is.
     */
    @Transactional
    public List<SettlementResponse> settleUp(Long groupId, LocalDate paidAt) {
        Group group = groupService.getGroup(groupId);
        List<Transfer> plan = debtSimplifier.simplify(balanceService.netBalances(group));
        if (plan.isEmpty()) {
            throw new BusinessException("Todos os saldos do grupo já estão zerados");
        }
        LocalDate date = paidAt != null ? paidAt : LocalDate.now(clock);
        List<SettlementResponse> recorded = new ArrayList<>();
        for (Transfer transfer : plan) {
            Settlement saved = settlementRepository.save(new Settlement(group, memberOf(group, transfer.fromMemberId()),
                    memberOf(group, transfer.toMemberId()), transfer.amount(), date));
            events.publishEvent(GroupActivityEvent.settlementRecorded(saved));
            recorded.add(SettlementResponse.from(saved));
        }
        return recorded;
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> findAll(Long groupId) {
        groupService.getGroup(groupId);
        return settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(groupId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    private static BigDecimal localAmount(Group group, CreateSettlementRequest request, BigDecimal limit) {
        if (request.exchangeRate() != null) {
            throw new BusinessException("A cotação só vale para acertos em outra moeda");
        }
        if (request.amount().compareTo(limit) > 0) {
            throw new BusinessException("O valor máximo desse acerto é %s"
                    .formatted(GroupActivityEvent.money(group.getCurrency(), limit)));
        }
        return request.amount();
    }

    /**
     * Converts a payment made in another currency. A foreign amount rarely lands on the exact
     * cent of the debt, so when it is less than one foreign cent away from the limit it pays the
     * debt off exactly instead of leaving a few cents behind or being rejected.
     */
    private static BigDecimal convertedAmount(Group group, String currency, CreateSettlementRequest request,
                                              BigDecimal limit) {
        BigDecimal rate = request.exchangeRate();
        if (rate == null) {
            throw new BusinessException("Informe a cotação de %s para %s".formatted(currency, group.getCurrency()));
        }
        BigDecimal converted = CurrencyConverter.convert(request.amount(), rate);
        if (CurrencyConverter.withinOneForeignCent(converted, limit, rate)) {
            return limit;
        }
        if (converted.compareTo(limit) > 0) {
            throw new BusinessException("O valor máximo desse acerto é %s (%s nessa cotação)".formatted(
                    GroupActivityEvent.money(group.getCurrency(), limit),
                    GroupActivityEvent.money(currency, CurrencyConverter.maxForeignAmount(limit, rate))));
        }
        return converted;
    }

    private static Member memberOf(Group group, Long memberId) {
        return group.findMember(memberId).orElseThrow(() -> ResourceNotFoundException.member(memberId));
    }
}
