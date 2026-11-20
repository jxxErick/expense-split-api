package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable Long groupId, @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.create(groupId, request);
    }

    @GetMapping
    public List<ExpenseResponse> findAll(@PathVariable Long groupId,
                                         @RequestParam(required = false) ExpenseCategory category) {
        return expenseService.findAll(groupId, category);
    }

    @PutMapping("/{expenseId}")
    public ExpenseResponse update(@PathVariable Long groupId, @PathVariable Long expenseId,
                                  @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.update(groupId, expenseId, request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long groupId, @PathVariable Long expenseId) {
        expenseService.delete(groupId, expenseId);
    }
}
