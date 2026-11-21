package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.BudgetRequest;
import com.jxxerick.expensesplit.dto.BudgetResponse;
import com.jxxerick.expensesplit.dto.BudgetStatusResponse;
import com.jxxerick.expensesplit.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<BudgetResponse> findAll(@PathVariable Long groupId) {
        return budgetService.findAll(groupId);
    }

    /** {@code scope} is TOTAL or a category (FOOD, BILLS...). */
    @PutMapping("/{scope}")
    public BudgetResponse upsert(@PathVariable Long groupId, @PathVariable String scope,
                                 @Valid @RequestBody BudgetRequest request) {
        return budgetService.upsert(groupId, scope, request);
    }

    @DeleteMapping("/{scope}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long groupId, @PathVariable String scope) {
        budgetService.delete(groupId, scope);
    }

    @GetMapping("/status")
    public BudgetStatusResponse status(@PathVariable Long groupId,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM")
                                       YearMonth month) {
        return budgetService.status(groupId, month);
    }
}
