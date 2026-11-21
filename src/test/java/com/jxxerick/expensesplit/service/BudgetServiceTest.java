package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.BudgetRequest;
import com.jxxerick.expensesplit.dto.BudgetResponse;
import com.jxxerick.expensesplit.dto.BudgetStatusResponse;
import com.jxxerick.expensesplit.dto.BudgetStatusResponse.Item;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Budget;
import com.jxxerick.expensesplit.model.BudgetLevel;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.BudgetRepository;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupService groupService;

    private BudgetService service;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, expenseRepository, groupService, CLOCK);
        group = TestData.group(1L, "Casa", "Ana", "Bruno");
        lenient().when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void statusOfCurrentMonthProjectsSpendingAndDailyAllowance() {
        when(budgetRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(
                new Budget(group, null, new BigDecimal("1000.00")),
                new Budget(group, ExpenseCategory.FOOD, new BigDecimal("300.00"))));
        when(expenseRepository.findByGroupIdAndSpentAtBetween(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .thenReturn(List.of(expense(ExpenseCategory.FOOD, "250.00"), expense(ExpenseCategory.BILLS, "400.00")));

        BudgetStatusResponse status = service.status(1L, null);

        assertThat(status.month()).isEqualTo("2026-11");
        assertThat(status.daysElapsed()).isEqualTo(10);
        assertThat(status.daysInMonth()).isEqualTo(30);
        assertThat(status.budgets()).extracting(Item::scope).containsExactly("FOOD", "TOTAL");

        Item food = status.budgets().get(0);
        assertThat(food.spent()).isEqualByComparingTo("250.00");
        assertThat(food.remaining()).isEqualByComparingTo("50.00");
        assertThat(food.percentUsed()).isEqualByComparingTo("83.33");
        assertThat(food.level()).isEqualTo(BudgetLevel.WARNING);
        assertThat(food.projectedSpend()).isEqualByComparingTo("750.00");
        assertThat(food.projectedLevel()).isEqualTo(BudgetLevel.EXCEEDED);
        assertThat(food.dailyAllowance()).isEqualByComparingTo("2.38");

        Item total = status.budgets().get(1);
        assertThat(total.spent()).isEqualByComparingTo("650.00");
        assertThat(total.level()).isEqualTo(BudgetLevel.OK);
        assertThat(total.dailyAllowance()).isEqualByComparingTo("16.66");
    }

    @Test
    void pastMonthUsesActualSpendingAndHasNoAllowance() {
        when(budgetRepository.findByGroupIdOrderByIdAsc(1L))
                .thenReturn(List.of(new Budget(group, ExpenseCategory.FOOD, new BigDecimal("100.00"))));
        when(expenseRepository.findByGroupIdAndSpentAtBetween(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(expense(ExpenseCategory.FOOD, "120.00")));

        BudgetStatusResponse status = service.status(1L, YearMonth.of(2026, 10));

        Item food = status.budgets().get(0);
        assertThat(status.daysElapsed()).isEqualTo(31);
        assertThat(food.remaining()).isEqualByComparingTo("-20.00");
        assertThat(food.level()).isEqualTo(BudgetLevel.EXCEEDED);
        assertThat(food.projectedSpend()).isEqualByComparingTo("120.00");
        assertThat(food.dailyAllowance()).isNull();
    }

    @Test
    void futureMonthStartsEmpty() {
        when(budgetRepository.findByGroupIdOrderByIdAsc(1L))
                .thenReturn(List.of(new Budget(group, null, new BigDecimal("500.00"))));

        BudgetStatusResponse status = service.status(1L, YearMonth.of(2027, 2));

        assertThat(status.daysElapsed()).isZero();
        assertThat(status.budgets().get(0).projectedSpend()).isEqualByComparingTo("0.00");
        assertThat(status.budgets().get(0).level()).isEqualTo(BudgetLevel.OK);
    }

    @Test
    void upsertChangesTheExistingLimit() {
        Budget food = new Budget(group, ExpenseCategory.FOOD, new BigDecimal("100.00"));
        when(budgetRepository.findByGroupIdAndCategory(1L, ExpenseCategory.FOOD)).thenReturn(Optional.of(food));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetResponse response = service.upsert(1L, "food", new BudgetRequest(new BigDecimal("150.00")));

        assertThat(response.scope()).isEqualTo("FOOD");
        assertThat(food.getMonthlyLimit()).isEqualByComparingTo("150.00");
    }

    @Test
    void categoryLimitCannotExceedTheTotal() {
        when(budgetRepository.findByGroupIdAndCategoryIsNull(1L))
                .thenReturn(Optional.of(new Budget(group, null, new BigDecimal("500.00"))));

        assertThatThrownBy(() -> service.upsert(1L, "FOOD", new BudgetRequest(new BigDecimal("600.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("orçamento total");
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void totalCannotBeBelowACategory() {
        when(budgetRepository.findByGroupIdOrderByIdAsc(1L))
                .thenReturn(List.of(new Budget(group, ExpenseCategory.LODGING, new BigDecimal("800.00"))));

        assertThatThrownBy(() -> service.upsert(1L, "TOTAL", new BudgetRequest(new BigDecimal("700.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LODGING");
    }

    @Test
    void rejectsUnknownScope() {
        assertThatThrownBy(() -> service.upsert(1L, "PETS", new BudgetRequest(BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PETS");
    }

    @Test
    void deleteFailsWhenThereIsNoBudget() {
        when(budgetRepository.findByGroupIdAndCategoryIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, "total")).isInstanceOf(ResourceNotFoundException.class);
    }

    private Expense expense(ExpenseCategory category, String amount) {
        return new Expense(group, "Gasto", new BigDecimal(amount), TestData.member(group, 1L), SplitType.EQUAL,
                category, LocalDate.of(2026, 11, 5));
    }
}
