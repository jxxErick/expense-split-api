package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.MemberStatementResponse;
import com.jxxerick.expensesplit.dto.StatementLineResponse;
import com.jxxerick.expensesplit.dto.StatementLineType;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate NOV_15 = LocalDate.of(2026, 11, 15);
    private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

    @Mock
    private GroupService groupService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    private StatementService statementService;
    private Group group;

    @BeforeEach
    void setUp() {
        statementService = new StatementService(groupService, expenseRepository, settlementRepository);
        group = TestData.group(1L, "Viagem", "Ana", "Bruno", "Carla");
        lenient().when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void listsMovementsOldestFirstWithRunningBalance() {
        given(List.of(
                        // Ana paid 90 for the three of them
                        expense(1L, "Hotel", NOV_10, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00"),
                        // Bruno paid 20 for Ana only
                        expense(2L, "Táxi", NOV_15, 2L, "20.00", 1L, "20.00"),
                        // Carla's dinner, Ana is not part of it
                        expense(3L, "Jantar", NOV_15, 3L, "40.00", 3L, "40.00")),
                List.of(settlement(4L, 2L, 1L, "10.00", NOV_20)));

        MemberStatementResponse statement = statementService.statement(1L, 1L, null, null);

        assertThat(statement.member().name()).isEqualTo("Ana");
        assertThat(statement.lines()).extracting(StatementLineResponse::description)
                .containsExactly("Hotel", "Táxi", "Pagamento de Bruno");
        assertThat(statement.lines()).extracting(StatementLineResponse::effect)
                .containsExactly(money("60.00"), money("-20.00"), money("-10.00"));
        assertThat(statement.lines()).extracting(StatementLineResponse::balance)
                .containsExactly(money("60.00"), money("40.00"), money("30.00"));
        assertThat(statement.lines().get(2).type()).isEqualTo(StatementLineType.SETTLEMENT_RECEIVED);
        assertThat(statement.openingBalance()).isEqualTo(money("0.00"));
        assertThat(statement.closingBalance()).isEqualTo(money("30.00"));
        assertThat(statement.totalPaid()).isEqualTo(money("90.00"));
        assertThat(statement.totalShare()).isEqualTo(money("50.00"));
        assertThat(statement.totalReceived()).isEqualTo(money("10.00"));
    }

    @Test
    void movementsBeforeThePeriodBecomeTheOpeningBalance() {
        given(List.of(
                        expense(1L, "Hotel", NOV_10, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00"),
                        expense(2L, "Táxi", NOV_15, 2L, "20.00", 2L, "20.00")),
                List.of(settlement(3L, 2L, 1L, "30.00", NOV_20)));

        MemberStatementResponse statement = statementService.statement(1L, 2L, NOV_15, NOV_15);

        assertThat(statement.openingBalance()).isEqualTo(money("-30.00"));
        assertThat(statement.lines()).singleElement().satisfies(line -> {
            assertThat(line.description()).isEqualTo("Táxi");
            assertThat(line.paid()).isEqualTo(money("20.00"));
            assertThat(line.share()).isEqualTo(money("20.00"));
            assertThat(line.effect()).isEqualTo(money("0.00"));
            assertThat(line.balance()).isEqualTo(money("-30.00"));
        });
        // the settlement on the 20th is after the period
        assertThat(statement.closingBalance()).isEqualTo(money("-30.00"));
        assertThat(statement.totalSent()).isEqualTo(money("0.00"));
    }

    @Test
    void sameDaySettlementComesAfterTheExpense() {
        given(List.of(expense(1L, "Mercado", NOV_10, 1L, "50.00", 1L, "25.00", 2L, "25.00")),
                List.of(settlement(2L, 2L, 1L, "25.00", NOV_10)));

        MemberStatementResponse statement = statementService.statement(1L, 2L, null, null);

        assertThat(statement.lines()).extracting(StatementLineResponse::type)
                .containsExactly(StatementLineType.EXPENSE, StatementLineType.SETTLEMENT_SENT);
        assertThat(statement.lines()).extracting(StatementLineResponse::balance)
                .containsExactly(money("-25.00"), money("0.00"));
        assertThat(statement.lines().get(1).description()).isEqualTo("Pagamento para Ana");
    }

    @Test
    void closingBalanceMatchesTheGroupBalance() {
        given(List.of(
                        expense(1L, "Hotel", NOV_10, 1L, "90.00", 1L, "30.00", 2L, "30.00", 3L, "30.00"),
                        expense(2L, "Táxi", NOV_15, 2L, "33.33", 1L, "11.11", 2L, "11.11", 3L, "11.11")),
                List.of(settlement(3L, 3L, 2L, "5.00", NOV_20)));
        BalanceService balanceService =
                new BalanceService(groupService, expenseRepository, settlementRepository, new DebtSimplifier());

        for (long memberId = 1; memberId <= 3; memberId++) {
            assertThat(statementService.statement(1L, memberId, null, null).closingBalance())
                    .isEqualTo(balanceService.netBalances(group).get(memberId));
        }
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> statementService.statement(1L, 1L, NOV_20, NOV_10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMemberFromAnotherGroup() {
        assertThatThrownBy(() -> statementService.statement(1L, 99L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Repositories return newest first, like the real queries. */
    private void given(List<Expense> expenses, List<Settlement> settlements) {
        List<Expense> newestExpenses = new ArrayList<>(expenses);
        newestExpenses.sort(Comparator.comparing(Expense::getSpentAt).thenComparing(Expense::getId).reversed());
        List<Settlement> newestSettlements = new ArrayList<>(settlements);
        newestSettlements.sort(Comparator.comparing(Settlement::getPaidAt).thenComparing(Settlement::getId).reversed());
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(newestExpenses);
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(newestSettlements);
    }

    private Expense expense(Long id, String description, LocalDate date, Long payerId, String amount,
                            Object... owed) {
        Expense expense = TestData.expense(group, payerId, amount, owed);
        ReflectionTestUtils.setField(expense, "id", id);
        ReflectionTestUtils.setField(expense, "description", description);
        ReflectionTestUtils.setField(expense, "spentAt", date);
        return expense;
    }

    private Settlement settlement(Long id, Long fromId, Long toId, String amount, LocalDate date) {
        Settlement settlement = TestData.settlement(group, fromId, toId, amount);
        ReflectionTestUtils.setField(settlement, "id", id);
        ReflectionTestUtils.setField(settlement, "paidAt", date);
        return settlement;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
