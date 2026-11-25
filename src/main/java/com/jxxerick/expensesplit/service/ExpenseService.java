package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.service.currency.CurrencyConverter;
import com.jxxerick.expensesplit.service.split.Share;
import com.jxxerick.expensesplit.service.split.SplitCalculator;
import com.jxxerick.expensesplit.service.split.SplitEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final SplitCalculator splitCalculator;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService,
                          SplitCalculator splitCalculator, Clock clock, ApplicationEventPublisher events) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.splitCalculator = splitCalculator;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public ExpenseResponse create(Long groupId, CreateExpenseRequest request) {
        Group group = groupService.getGroup(groupId);
        Member payer = activeMemberOf(group, request.payerId());
        Conversion conversion = convert(group, request);

        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : LocalDate.now(clock);
        Expense expense = new Expense(group, request.description().trim(), conversion.amount(), payer,
                request.splitType(), request.categoryOrDefault(), spentAt);
        conversion.applyTo(expense, request);
        addShares(group, expense, conversion.shares());

        Expense saved = expenseRepository.save(expense);
        events.publishEvent(GroupActivityEvent.expenseCreated(saved));
        return ExpenseResponse.from(saved);
    }

    /** Replaces every field and recalculates the split; a missing date keeps the current one. */
    @Transactional
    public ExpenseResponse update(Long groupId, Long expenseId, CreateExpenseRequest request) {
        Group group = groupService.getGroup(groupId);
        Expense expense = findInGroup(groupId, expenseId);
        ensureOnlyActiveMembers(expense);
        Member payer = activeMemberOf(group, request.payerId());
        Conversion conversion = convert(group, request);

        BigDecimal previousAmount = expense.getAmount();
        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : expense.getSpentAt();
        expense.update(request.description().trim(), conversion.amount(), payer, request.splitType(),
                request.categoryOrDefault(), spentAt);
        conversion.applyTo(expense, request);
        addShares(group, expense, conversion.shares());

        Expense saved = expenseRepository.save(expense);
        events.publishEvent(GroupActivityEvent.expenseUpdated(saved, previousAmount));
        return ExpenseResponse.from(saved);
    }

    /** A null category lists every expense. */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> findAll(Long groupId, ExpenseCategory category) {
        groupService.getGroup(groupId);
        List<Expense> expenses = category == null
                ? expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(groupId)
                : expenseRepository.findByGroupIdAndCategoryOrderBySpentAtDescIdDesc(groupId, category);
        return expenses.stream().map(ExpenseResponse::from).toList();
    }

    @Transactional
    public void delete(Long groupId, Long expenseId) {
        Expense expense = findInGroup(groupId, expenseId);
        ensureOnlyActiveMembers(expense);
        expenseRepository.delete(expense);
        events.publishEvent(GroupActivityEvent.expenseDeleted(expense));
    }

    private Expense findInGroup(Long groupId, Long expenseId) {
        return expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Despesa %d não encontrada no grupo %d".formatted(expenseId, groupId)));
    }

    /** Validates members and split rules; used before persisting anything. */
    List<Share> calculateShares(Group group, CreateExpenseRequest request) {
        return calculateShares(group, request, request.amount());
    }

    private List<Share> calculateShares(Group group, CreateExpenseRequest request, BigDecimal total) {
        List<SplitEntry> entries = request.shares().stream()
                .map(s -> new SplitEntry(activeMemberOf(group, s.memberId()).getId(), s.value()))
                .toList();
        return splitCalculator.calculate(request.splitType(), total, entries);
    }

    /**
     * Validates the split in the currency the expense was paid in (EXACT values match the receipt),
     * then brings it to the group currency: EXACT shares are converted proportionally, while the
     * other types are split again over the converted total, so an EQUAL split stays equal to the cent.
     */
    Conversion convert(Group group, CreateExpenseRequest request) {
        String currency = request.currency() == null ? group.getCurrency()
                : CurrencyConverter.normalize(request.currency());
        List<Share> shares = calculateShares(group, request);
        if (currency.equals(group.getCurrency())) {
            if (request.exchangeRate() != null) {
                throw new BusinessException("A cotação só vale para despesas em outra moeda");
            }
            return new Conversion(null, request.amount(), shares);
        }
        if (request.exchangeRate() == null) {
            throw new BusinessException("Informe a cotação de %s para %s".formatted(currency, group.getCurrency()));
        }
        BigDecimal converted = CurrencyConverter.convert(request.amount(), request.exchangeRate());
        List<Share> convertedShares = request.splitType() == SplitType.EXACT
                ? CurrencyConverter.convertShares(shares, converted)
                : calculateShares(group, request, converted);
        return new Conversion(currency, converted, convertedShares);
    }

    /** Amount and shares in the group currency; {@code foreignCurrency} is null for local expenses. */
    record Conversion(String foreignCurrency, BigDecimal amount, List<Share> shares) {

        void applyTo(Expense expense, CreateExpenseRequest request) {
            if (foreignCurrency != null) {
                expense.paidInForeignCurrency(foreignCurrency, request.amount(), request.exchangeRate());
            }
        }
    }

    private static void addShares(Group group, Expense expense, List<Share> shares) {
        shares.forEach(share -> expense.addShare(activeMemberOf(group, share.memberId()), share.amount()));
    }

    static Member memberOf(Group group, Long memberId) {
        return group.findMember(memberId).orElseThrow(() -> ResourceNotFoundException.member(memberId));
    }

    /** New expenses only involve people still in the group. */
    static Member activeMemberOf(Group group, Long memberId) {
        Member member = memberOf(group, memberId);
        if (!member.isActive()) {
            throw new BusinessException("%s saiu do grupo".formatted(member.getName()));
        }
        return member;
    }

    /** Changing an expense of someone who left would reopen a balance they already closed. */
    private static void ensureOnlyActiveMembers(Expense expense) {
        Stream.concat(Stream.of(expense.getPayer()), expense.getShares().stream().map(ExpenseShare::getMember))
                .filter(member -> !member.isActive())
                .findFirst()
                .ifPresent(member -> {
                    throw new BusinessException("%s saiu do grupo; essa despesa não pode mais ser alterada"
                            .formatted(member.getName()));
                });
    }
}
