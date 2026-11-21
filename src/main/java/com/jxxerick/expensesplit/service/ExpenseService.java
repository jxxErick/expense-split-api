package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
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
        Member payer = memberOf(group, request.payerId());
        List<Share> shares = calculateShares(group, request);

        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : LocalDate.now(clock);
        Expense expense = new Expense(group, request.description().trim(), request.amount(), payer,
                request.splitType(), request.categoryOrDefault(), spentAt);
        addShares(group, expense, shares);

        Expense saved = expenseRepository.save(expense);
        events.publishEvent(GroupActivityEvent.expenseCreated(saved));
        return ExpenseResponse.from(saved);
    }

    /** Replaces every field and recalculates the split; a missing date keeps the current one. */
    @Transactional
    public ExpenseResponse update(Long groupId, Long expenseId, CreateExpenseRequest request) {
        Group group = groupService.getGroup(groupId);
        Expense expense = findInGroup(groupId, expenseId);
        Member payer = memberOf(group, request.payerId());
        List<Share> shares = calculateShares(group, request);

        BigDecimal previousAmount = expense.getAmount();
        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : expense.getSpentAt();
        expense.update(request.description().trim(), request.amount(), payer, request.splitType(),
                request.categoryOrDefault(), spentAt);
        addShares(group, expense, shares);

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
        List<SplitEntry> entries = request.shares().stream()
                .map(s -> new SplitEntry(memberOf(group, s.memberId()).getId(), s.value()))
                .toList();
        return splitCalculator.calculate(request.splitType(), request.amount(), entries);
    }

    private static void addShares(Group group, Expense expense, List<Share> shares) {
        shares.forEach(share -> expense.addShare(memberOf(group, share.memberId()), share.amount()));
    }

    static Member memberOf(Group group, Long memberId) {
        return group.findMember(memberId).orElseThrow(() -> ResourceNotFoundException.member(memberId));
    }
}
