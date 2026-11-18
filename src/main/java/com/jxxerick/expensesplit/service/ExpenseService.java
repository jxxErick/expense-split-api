package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.split.Share;
import com.jxxerick.expensesplit.service.split.SplitCalculator;
import com.jxxerick.expensesplit.service.split.SplitEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final SplitCalculator splitCalculator;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService,
                          SplitCalculator splitCalculator, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.splitCalculator = splitCalculator;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(Long groupId, CreateExpenseRequest request) {
        Group group = groupService.getGroup(groupId);
        Member payer = memberOf(group, request.payerId());

        List<SplitEntry> entries = request.shares().stream()
                .map(s -> new SplitEntry(memberOf(group, s.memberId()).getId(), s.value()))
                .toList();
        List<Share> shares = splitCalculator.calculate(request.splitType(), request.amount(), entries);

        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : LocalDate.now(clock);
        Expense expense = new Expense(group, request.description().trim(), request.amount(), payer,
                request.splitType(), spentAt);
        shares.forEach(share -> expense.addShare(memberOf(group, share.memberId()), share.amount()));

        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> findAll(Long groupId) {
        groupService.getGroup(groupId);
        return expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(groupId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long groupId, Long expenseId) {
        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Despesa %d não encontrada no grupo %d".formatted(expenseId, groupId)));
        expenseRepository.delete(expense);
    }

    private static Member memberOf(Group group, Long memberId) {
        return group.findMember(memberId).orElseThrow(() -> ResourceNotFoundException.member(memberId));
    }
}
