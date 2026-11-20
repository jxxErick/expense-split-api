package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.SpendingReportResponse;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.CategoryTotal;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MemberSpending;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MonthlyTotal;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private GroupService groupService;

    @Mock
    private ExpenseRepository expenseRepository;

    private ReportService reportService;
    private Group group;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(groupService, expenseRepository);
        group = TestData.group(1L, "Casa", "Ana", "Bruno");
    }

    @Test
    void summarizesTotalsPerMonthAndMember() {
        givenExpenses(
                spentOn(TestData.expense(group, 1L, "100.00", 1L, "50.00", 2L, "50.00"), 2026, 10, 5),
                spentOn(TestData.expense(group, 2L, "60.00", 2L, "60.00"), 2026, 11, 2),
                spentOn(TestData.expense(group, 1L, "40.00", 1L, "20.00", 2L, "20.00"), 2026, 11, 9));

        SpendingReportResponse report = reportService.spending(1L, null, null);

        assertThat(report.total()).isEqualByComparingTo("200.00");
        assertThat(report.expenseCount()).isEqualTo(3);
        assertThat(report.averageExpense()).isEqualByComparingTo("66.67");
        assertThat(report.months()).containsExactly(
                new MonthlyTotal("2026-10", money("100.00"), 1),
                new MonthlyTotal("2026-11", money("100.00"), 2));
        assertThat(report.members()).containsExactly(
                new MemberSpending(1L, "Ana", money("140.00"), money("70.00"), money("35.00")),
                new MemberSpending(2L, "Bruno", money("60.00"), money("130.00"), money("65.00")));
    }

    @Test
    void groupsTotalsByCategoryHighestFirst() {
        givenExpenses(
                inCategory(TestData.expense(group, 1L, "30.00", 1L, "30.00"), ExpenseCategory.FOOD),
                inCategory(TestData.expense(group, 2L, "120.00", 2L, "120.00"), ExpenseCategory.LODGING),
                inCategory(TestData.expense(group, 1L, "50.00", 2L, "50.00"), ExpenseCategory.FOOD));

        SpendingReportResponse report = reportService.spending(1L, null, null);

        assertThat(report.categories()).containsExactly(
                new CategoryTotal(ExpenseCategory.LODGING, money("120.00"), 1, money("60.00")),
                new CategoryTotal(ExpenseCategory.FOOD, money("80.00"), 2, money("40.00")));
    }

    @Test
    void keepsOnlyExpensesInsideThePeriod() {
        givenExpenses(
                spentOn(TestData.expense(group, 1L, "100.00", 1L, "100.00"), 2026, 10, 31),
                spentOn(TestData.expense(group, 2L, "30.00", 2L, "30.00"), 2026, 11, 1),
                spentOn(TestData.expense(group, 2L, "10.00", 2L, "10.00"), 2026, 11, 30));

        SpendingReportResponse report = reportService.spending(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(report.total()).isEqualByComparingTo("40.00");
        assertThat(report.months()).extracting(MonthlyTotal::month).containsExactly("2026-11");
    }

    @Test
    void returnsZerosWhenThereAreNoExpenses() {
        givenExpenses();

        SpendingReportResponse report = reportService.spending(1L, null, null);

        assertThat(report.total()).isEqualByComparingTo("0.00");
        assertThat(report.averageExpense()).isEqualByComparingTo("0.00");
        assertThat(report.months()).isEmpty();
        assertThat(report.categories()).isEmpty();
        assertThat(report.members()).extracting(MemberSpending::percentage).containsOnly(money("0.00"));
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> reportService.spending(1L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(groupService, expenseRepository);
    }

    private void givenExpenses(Expense... expenses) {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(List.of(expenses));
    }

    private static Expense spentOn(Expense expense, int year, int month, int day) {
        ReflectionTestUtils.setField(expense, "spentAt", LocalDate.of(year, month, day));
        return expense;
    }

    private static Expense inCategory(Expense expense, ExpenseCategory category) {
        ReflectionTestUtils.setField(expense, "category", category);
        return expense;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
