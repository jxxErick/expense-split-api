package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.BudgetRequest;
import com.jxxerick.expensesplit.dto.BudgetResponse;
import com.jxxerick.expensesplit.dto.BudgetStatusResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Budget;
import com.jxxerick.expensesplit.model.BudgetLevel;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.repository.BudgetRepository;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BudgetService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;
    private final Clock clock;

    public BudgetService(BudgetRepository budgetRepository, ExpenseRepository expenseRepository,
                         GroupService groupService, Clock clock) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
        this.clock = clock;
    }

    /** Creates or replaces the limit of a scope (TOTAL or a category). */
    @Transactional
    public BudgetResponse upsert(Long groupId, String scope, BudgetRequest request) {
        Group group = groupService.getGroup(groupId);
        ExpenseCategory category = parseScope(scope);
        BigDecimal limit = request.monthlyLimit();
        checkAgainstOtherBudgets(groupId, category, limit);

        Budget budget = find(groupId, category)
                .map(existing -> {
                    existing.changeLimit(limit);
                    return existing;
                })
                .orElseGet(() -> new Budget(group, category, limit));
        return BudgetResponse.from(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> findAll(Long groupId) {
        groupService.getGroup(groupId);
        return budgetRepository.findByGroupIdOrderByIdAsc(groupId).stream().map(BudgetResponse::from).toList();
    }

    @Transactional
    public void delete(Long groupId, String scope) {
        ExpenseCategory category = parseScope(scope);
        Budget budget = find(groupId, category).orElseThrow(() -> new ResourceNotFoundException(
                "Não há orçamento %s no grupo %d".formatted(scope.toUpperCase(Locale.ROOT), groupId)));
        budgetRepository.delete(budget);
    }

    /** A null month means the current one; budgets come sorted from the most to the least used. */
    @Transactional(readOnly = true)
    public BudgetStatusResponse status(Long groupId, YearMonth requestedMonth) {
        groupService.getGroup(groupId);
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        YearMonth month = requestedMonth != null ? requestedMonth : current;

        int daysInMonth = month.lengthOfMonth();
        int daysElapsed;
        if (month.isBefore(current)) {
            daysElapsed = daysInMonth;
        } else if (month.equals(current)) {
            daysElapsed = today.getDayOfMonth();
        } else {
            daysElapsed = 0;
        }
        boolean running = month.equals(current);

        List<Expense> expenses = expenseRepository.findByGroupIdAndSpentAtBetween(
                groupId, month.atDay(1), month.atEndOfMonth());
        List<BudgetStatusResponse.Item> items = budgetRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .map(budget -> item(budget, expenses, daysElapsed, daysInMonth, running))
                .sorted(Comparator.comparing(BudgetStatusResponse.Item::percentUsed).reversed())
                .toList();
        return new BudgetStatusResponse(month.toString(), daysElapsed, daysInMonth, items);
    }

    private static BudgetStatusResponse.Item item(Budget budget, List<Expense> expenses, int daysElapsed,
                                                  int daysInMonth, boolean running) {
        BigDecimal limit = budget.getMonthlyLimit();
        BigDecimal spent = expenses.stream()
                .filter(e -> budget.covers(e.getCategory()))
                .map(Expense::getAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal remaining = limit.subtract(spent);
        BigDecimal percentUsed = spent.multiply(HUNDRED).divide(limit, 2, RoundingMode.HALF_EVEN);

        BigDecimal projected = daysElapsed == 0 ? spent
                : spent.multiply(BigDecimal.valueOf(daysInMonth)).divide(BigDecimal.valueOf(daysElapsed), 2,
                RoundingMode.HALF_EVEN);
        // today still counts as a day to spend
        BigDecimal dailyAllowance = running
                ? remaining.max(ZERO).divide(BigDecimal.valueOf(daysInMonth - daysElapsed + 1L), 2, RoundingMode.DOWN)
                : null;

        return new BudgetStatusResponse.Item(BudgetResponse.scopeOf(budget), budget.getCategory(), limit, spent,
                remaining, percentUsed, BudgetLevel.of(spent, limit), projected, BudgetLevel.of(projected, limit),
                dailyAllowance);
    }

    /** A category limit can't be above the total limit, and the total can't be below any category. */
    private void checkAgainstOtherBudgets(Long groupId, ExpenseCategory category, BigDecimal limit) {
        if (category != null) {
            budgetRepository.findByGroupIdAndCategoryIsNull(groupId)
                    .filter(total -> limit.compareTo(total.getMonthlyLimit()) > 0)
                    .ifPresent(total -> {
                        throw new BusinessException("O limite de %s não pode passar do orçamento total (R$ %s)"
                                .formatted(category, total.getMonthlyLimit()));
                    });
            return;
        }
        budgetRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .filter(b -> !b.isTotal() && b.getMonthlyLimit().compareTo(limit) > 0)
                .findFirst()
                .ifPresent(b -> {
                    throw new BusinessException("O orçamento total não pode ser menor que o de %s (R$ %s)"
                            .formatted(b.getCategory(), b.getMonthlyLimit()));
                });
    }

    private Optional<Budget> find(Long groupId, ExpenseCategory category) {
        return category == null
                ? budgetRepository.findByGroupIdAndCategoryIsNull(groupId)
                : budgetRepository.findByGroupIdAndCategory(groupId, category);
    }

    /** TOTAL maps to null (whole group); anything else must be a category name. */
    static ExpenseCategory parseScope(String scope) {
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (BudgetResponse.TOTAL_SCOPE.equals(normalized)) {
            return null;
        }
        try {
            return ExpenseCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Escopo de orçamento inválido: %s (use TOTAL ou uma categoria)".formatted(scope));
        }
    }
}
