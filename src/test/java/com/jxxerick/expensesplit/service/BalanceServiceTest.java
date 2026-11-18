package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.BalanceResponse;
import com.jxxerick.expensesplit.dto.TransferResponse;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.balance.DebtSimplifier;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @Mock
    private GroupService groupService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    private BalanceService balanceService;
    private Group group;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(groupService, expenseRepository, settlementRepository, new DebtSimplifier());
        group = TestData.group(1L, "Viagem", "Ana", "Bruno", "Carla");
        when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void computesPaidOwedAndNetBalancePerMember() {
        given(List.of(
                // Ana paid 90 for everyone
                TestData.expense(group, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00"),
                // Bruno paid 20 for Carla only
                TestData.expense(group, 2L, "20.00", 3L, "20.00")), List.of());

        List<BalanceResponse> balances = balanceService.balances(1L);

        assertThat(balances).containsExactly(
                new BalanceResponse(1L, "Ana", money("90.00"), money("30.00"), ZERO, ZERO, money("60.00")),
                new BalanceResponse(2L, "Bruno", money("20.00"), money("30.00"), ZERO, ZERO, money("-10.00")),
                new BalanceResponse(3L, "Carla", ZERO, money("50.00"), ZERO, ZERO, money("-50.00")));
    }

    @Test
    void settlementsMoveBalancesTowardsZero() {
        given(List.of(TestData.expense(group, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00")),
                List.of(TestData.settlement(group, 2L, 1L, "30.00"), TestData.settlement(group, 3L, 1L, "10.00")));

        List<BalanceResponse> balances = balanceService.balances(1L);

        assertThat(balances).extracting(BalanceResponse::balance)
                .containsExactly(money("20.00"), money("0.00"), money("-20.00"));
        assertThat(balances.get(0).received()).isEqualTo(money("40.00"));
        assertThat(balances.get(1).sent()).isEqualTo(money("30.00"));
    }

    @Test
    void listsMembersWithoutExpensesWithZeroBalance() {
        given(List.of(), List.of());

        assertThat(balanceService.balances(1L)).extracting(BalanceResponse::balance).containsOnly(ZERO);
    }

    @Test
    void simplifiesCircularDebtsAway() {
        // Ana pays for Bruno, Bruno pays for Carla, Carla pays for Ana: everybody is even
        given(List.of(
                TestData.expense(group, 1L, "25.00", 2L, "25.00"),
                TestData.expense(group, 2L, "25.00", 3L, "25.00"),
                TestData.expense(group, 3L, "25.00", 1L, "25.00")), List.of());

        assertThat(balanceService.simplifiedDebts(1L)).isEmpty();
    }

    @Test
    void suggestsTransfersWithMemberNames() {
        given(List.of(
                TestData.expense(group, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00"),
                TestData.expense(group, 2L, "20.00", 3L, "20.00")), List.of());

        List<TransferResponse> transfers = balanceService.simplifiedDebts(1L);

        assertThat(transfers).hasSize(2);
        assertThat(transfers.get(0).from().name()).isEqualTo("Carla");
        assertThat(transfers.get(0).to().name()).isEqualTo("Ana");
        assertThat(transfers.get(0).amount()).isEqualByComparingTo("50.00");
        assertThat(transfers.get(1).from().name()).isEqualTo("Bruno");
        assertThat(transfers.get(1).amount()).isEqualByComparingTo("10.00");
    }

    private void given(List<Expense> expenses, List<Settlement> settlements) {
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(expenses);
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(settlements);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
