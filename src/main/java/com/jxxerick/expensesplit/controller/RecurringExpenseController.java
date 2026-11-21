package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.CreateRecurringExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.RecurringExpenseResponse;
import com.jxxerick.expensesplit.service.RecurringExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/recurring-expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringExpenseResponse create(@PathVariable Long groupId,
                                           @Valid @RequestBody CreateRecurringExpenseRequest request) {
        return recurringExpenseService.create(groupId, request);
    }

    @GetMapping
    public List<RecurringExpenseResponse> findAll(@PathVariable Long groupId) {
        return recurringExpenseService.findAll(groupId);
    }

    @PostMapping("/{recurringId}/pause")
    public RecurringExpenseResponse pause(@PathVariable Long groupId, @PathVariable Long recurringId) {
        return recurringExpenseService.pause(groupId, recurringId);
    }

    @PostMapping("/{recurringId}/resume")
    public RecurringExpenseResponse resume(@PathVariable Long groupId, @PathVariable Long recurringId) {
        return recurringExpenseService.resume(groupId, recurringId);
    }

    @DeleteMapping("/{recurringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long groupId, @PathVariable Long recurringId) {
        recurringExpenseService.delete(groupId, recurringId);
    }

    /** Generates every due occurrence now; returns the expenses created. */
    @PostMapping("/run")
    public List<ExpenseResponse> runDue(@PathVariable Long groupId) {
        return recurringExpenseService.runDue(groupId);
    }
}
