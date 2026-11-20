package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.ShareRequest;
import com.jxxerick.expensesplit.dto.ShareResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.split.EqualSplitStrategy;
import com.jxxerick.expensesplit.service.split.ExactSplitStrategy;
import com.jxxerick.expensesplit.service.split.PercentageSplitStrategy;
import com.jxxerick.expensesplit.service.split.SharesSplitStrategy;
import com.jxxerick.expensesplit.service.split.SplitCalculator;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupService groupService;

    private ExpenseService expenseService;
    private Group group;

    @BeforeEach
    void setUp() {
        SplitCalculator calculator = new SplitCalculator(List.of(
                new EqualSplitStrategy(), new ExactSplitStrategy(), new PercentageSplitStrategy(), new SharesSplitStrategy()));
        expenseService = new ExpenseService(expenseRepository, groupService, calculator, CLOCK);
        group = TestData.group(1L, "Viagem", "Ana", "Bruno", "Carla");
    }

    @Test
    void createsExpenseSplittingEqually() {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = expenseService.create(1L, request(SplitType.EQUAL, "100.00",
                share(1L, null), share(2L, null), share(3L, null)));

        assertThat(response.payer().name()).isEqualTo("Ana");
        assertThat(response.spentAt()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(response.shares()).extracting(ShareResponse::amount)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    }

    @Test
    void payerDoesNotNeedToParticipate() {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = expenseService.create(1L, request(SplitType.PERCENTAGE, "80.00",
                share(2L, "75"), share(3L, "25")));

        assertThat(response.shares()).extracting(ShareResponse::memberName).containsExactly("Bruno", "Carla");
        assertThat(response.shares()).extracting(ShareResponse::amount)
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("20.00"));
    }

    @Test
    void rejectsParticipantFromAnotherGroup() {
        when(groupService.getGroup(1L)).thenReturn(group);

        assertThatThrownBy(() -> expenseService.create(1L, request(SplitType.EQUAL, "10.00", share(1L, null), share(42L, null))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void deleteFailsWhenExpenseBelongsToAnotherGroup() {
        when(expenseRepository.findByIdAndGroupId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.delete(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void updateRecalculatesSharesAndKeepsDateWhenMissing() {
        Expense existing = TestData.expense(group, 1L, "30.00", 1L, "10.00", 2L, "10.00", 3L, "10.00");
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = expenseService.update(1L, 5L, new CreateExpenseRequest(" Mercado ",
                new BigDecimal("90.00"), 2L, SplitType.SHARES, null, List.of(share(1L, "2"), share(3L, "1"))));

        assertThat(response.description()).isEqualTo("Mercado");
        assertThat(response.payer().name()).isEqualTo("Bruno");
        assertThat(response.spentAt()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(response.shares()).extracting(ShareResponse::memberName).containsExactly("Ana", "Carla");
        assertThat(response.shares()).extracting(ShareResponse::amount)
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("30.00"));
    }

    @Test
    void updateKeepsExpenseUntouchedWhenSplitIsInvalid() {
        Expense existing = TestData.expense(group, 1L, "30.00", 1L, "30.00");
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> expenseService.update(1L, 5L,
                request(SplitType.PERCENTAGE, "50.00", share(1L, "60"), share(2L, "30"))))
                .isInstanceOf(BusinessException.class);
        assertThat(existing.getAmount()).isEqualByComparingTo("30.00");
        assertThat(existing.getShares()).hasSize(1);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateFailsWhenExpenseIsNotInGroup() {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.findByIdAndGroupId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.update(1L, 9L, request(SplitType.EQUAL, "10.00", share(1L, null))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9");
    }

    private static CreateExpenseRequest request(SplitType type, String amount, ShareRequest... shares) {
        return new CreateExpenseRequest("Jantar", new BigDecimal(amount), 1L, type, null, List.of(shares));
    }

    private static ShareRequest share(Long memberId, String value) {
        return new ShareRequest(memberId, value == null ? null : new BigDecimal(value));
    }
}
