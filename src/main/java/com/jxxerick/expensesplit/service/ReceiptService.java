package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitRequest;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse.MemberBillResponse;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.service.receipt.ReceiptBill;
import com.jxxerick.expensesplit.service.receipt.ReceiptItem;
import com.jxxerick.expensesplit.service.receipt.ReceiptSplitter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Splits a bill item by item and records it as a single EXACT expense. */
@Service
public class ReceiptService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ReceiptService(ExpenseRepository expenseRepository, GroupService groupService, Clock clock,
                          ApplicationEventPublisher events) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.clock = clock;
        this.events = events;
    }

    /** Same validation as {@link #create}, but nothing is saved. */
    @Transactional(readOnly = true)
    public ReceiptSplitResponse preview(Long groupId, ReceiptSplitRequest request) {
        Group group = groupService.getGroup(groupId);
        ExpenseService.activeMemberOf(group, request.payerId());
        ReceiptBill bill = split(group, request);

        List<MemberBillResponse> members = bill.members().stream()
                .map(m -> new MemberBillResponse(m.memberId(), ExpenseService.memberOf(group, m.memberId()).getName(),
                        m.subtotal(), m.serviceCharge(), m.discount(), m.total()))
                .toList();
        return new ReceiptSplitResponse(bill.subtotal(), bill.serviceCharge(), bill.discount(), bill.total(), members);
    }

    @Transactional
    public ExpenseResponse create(Long groupId, ReceiptSplitRequest request) {
        Group group = groupService.getGroup(groupId);
        Member payer = ExpenseService.activeMemberOf(group, request.payerId());
        ReceiptBill bill = split(group, request);

        LocalDate spentAt = request.spentAt() != null ? request.spentAt() : LocalDate.now(clock);
        Expense expense = new Expense(group, request.description().trim(), bill.total(), payer, SplitType.EXACT,
                request.categoryOrDefault(), spentAt);
        // someone whose only item rounded down to zero owes nothing
        bill.members().stream()
                .filter(m -> m.total().signum() > 0)
                .forEach(m -> expense.addShare(ExpenseService.memberOf(group, m.memberId()), m.total()));

        Expense saved = expenseRepository.save(expense);
        events.publishEvent(GroupActivityEvent.expenseCreated(saved));
        return ExpenseResponse.from(saved);
    }

    private static ReceiptBill split(Group group, ReceiptSplitRequest request) {
        List<ReceiptItem> items = request.items().stream()
                .map(item -> new ReceiptItem(item.description().trim(), item.unitPrice(), item.quantityOrDefault(),
                        item.memberIds().stream().map(id -> ExpenseService.activeMemberOf(group, id).getId()).toList()))
                .toList();
        return ReceiptSplitter.split(items, request.serviceChargePercent(), request.discount());
    }
}
