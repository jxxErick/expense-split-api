package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.CreateRecurringExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.RecurringExpenseResponse;
import com.jxxerick.expensesplit.dto.ShareRequest;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringShare;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.repository.RecurringExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecurringExpenseService {

    /** Most occurrences one template can catch up on in a single run (e.g. after downtime). */
    static final int MAX_CATCH_UP = 24;

    private final RecurringExpenseRepository recurringRepository;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final Clock clock;

    public RecurringExpenseService(RecurringExpenseRepository recurringRepository, GroupService groupService,
                                   ExpenseService expenseService, Clock clock) {
        this.recurringRepository = recurringRepository;
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.clock = clock;
    }

    /** Validates the split up front, so generation never fails later on. */
    @Transactional
    public RecurringExpenseResponse create(Long groupId, CreateRecurringExpenseRequest request) {
        if (request.endOn() != null && request.endOn().isBefore(request.startOn())) {
            throw new BusinessException("A data final deve ser igual ou posterior à primeira cobrança");
        }
        Group group = groupService.getGroup(groupId);
        Member payer = ExpenseService.memberOf(group, request.payerId());
        expenseService.calculateShares(group, request.toExpenseRequest(request.startOn()));

        List<RecurringShare> shares = request.shares().stream()
                .map(s -> new RecurringShare(s.memberId(), s.value()))
                .toList();
        RecurringExpense recurring = new RecurringExpense(group, request.description().trim(), request.amount(),
                payer, request.splitType(), request.categoryOrDefault(), request.frequency(), request.startOn(),
                request.endOn(), shares);
        return RecurringExpenseResponse.from(recurringRepository.save(recurring));
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> findAll(Long groupId) {
        groupService.getGroup(groupId);
        return recurringRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .map(RecurringExpenseResponse::from)
                .toList();
    }

    @Transactional
    public RecurringExpenseResponse pause(Long groupId, Long recurringId) {
        RecurringExpense recurring = findInGroup(groupId, recurringId);
        ensureNotFinished(recurring);
        recurring.pause();
        return RecurringExpenseResponse.from(recurring);
    }

    @Transactional
    public RecurringExpenseResponse resume(Long groupId, Long recurringId) {
        RecurringExpense recurring = findInGroup(groupId, recurringId);
        ensureNotFinished(recurring);
        recurring.resume(LocalDate.now(clock));
        return RecurringExpenseResponse.from(recurring);
    }

    /** Expenses already generated stay in the group. */
    @Transactional
    public void delete(Long groupId, Long recurringId) {
        recurringRepository.delete(findInGroup(groupId, recurringId));
    }

    /** Generates the group's due occurrences right away instead of waiting for the scheduler. */
    @Transactional
    public List<ExpenseResponse> runDue(Long groupId) {
        groupService.getGroup(groupId);
        LocalDate today = LocalDate.now(clock);
        List<ExpenseResponse> created = new ArrayList<>();
        for (RecurringExpense recurring : recurringRepository.findByGroupIdOrderByIdAsc(groupId)) {
            created.addAll(generate(recurring, today));
        }
        return created;
    }

    /** Called by the scheduler for every group; returns how many expenses were created. */
    @Transactional
    public int generateAllDue() {
        LocalDate today = LocalDate.now(clock);
        return recurringRepository.findByStatusAndNextRunOnLessThanEqualOrderByIdAsc(RecurringStatus.ACTIVE, today)
                .stream()
                .mapToInt(recurring -> generate(recurring, today).size())
                .sum();
    }

    private List<ExpenseResponse> generate(RecurringExpense recurring, LocalDate today) {
        List<ExpenseResponse> created = new ArrayList<>();
        while (recurring.isDue(today) && created.size() < MAX_CATCH_UP) {
            LocalDate spentAt = recurring.advance();
            created.add(expenseService.create(recurring.getGroup().getId(), toExpenseRequest(recurring, spentAt)));
        }
        return created;
    }

    private static CreateExpenseRequest toExpenseRequest(RecurringExpense recurring, LocalDate spentAt) {
        List<ShareRequest> shares = recurring.getShares().stream()
                .map(s -> new ShareRequest(s.getMemberId(), s.getValue()))
                .toList();
        return new CreateExpenseRequest(recurring.getDescription(), recurring.getAmount(),
                recurring.getPayer().getId(), recurring.getSplitType(), recurring.getCategory(), spentAt, shares);
    }

    private static void ensureNotFinished(RecurringExpense recurring) {
        if (recurring.getStatus() == RecurringStatus.FINISHED) {
            throw new BusinessException("Essa despesa recorrente já terminou");
        }
    }

    private RecurringExpense findInGroup(Long groupId, Long recurringId) {
        return recurringRepository.findByIdAndGroupId(recurringId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Despesa recorrente %d não encontrada no grupo %d".formatted(recurringId, groupId)));
    }
}
