package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.DebtAgingResponse;
import com.jxxerick.expensesplit.dto.DebtAgingResponse.MemberAging;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.balance.AgingBucket;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtAgingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private GroupService groupService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    private DebtAgingService service;
    private Group group;
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Settlement> settlements = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DebtAgingService(groupService, expenseRepository, settlementRepository, CLOCK);
        group = TestData.group(1L, "Casa", "Ana", "Bruno", "Carla");
        lenient().when(groupService.getGroup(1L)).thenReturn(group);
        // repositories return newest first
        lenient().when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenAnswer(inv -> expenses.reversed());
        lenient().when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenAnswer(inv -> settlements.reversed());
    }

    @Test
    void agesEachDebtorOldestFirst() {
        // Ana pays everything; Bruno owes since August, Carla only since November
        expense(LocalDate.of(2026, 8, 10), 1L, "60.00", 1L, "20.00", 2L, "40.00");
        expense(LocalDate.of(2026, 11, 20), 1L, "30.00", 1L, "15.00", 3L, "15.00");
        expense(LocalDate.of(2026, 10, 5), 1L, "20.00", 1L, "10.00", 2L, "10.00");

        DebtAgingResponse response = service.aging(1L, null);

        assertThat(response.asOf()).isEqualTo(TODAY);
        assertThat(response.totalOwed()).isEqualByComparingTo("65.00");
        assertThat(response.members()).extracting(m -> m.member().name()).containsExactly("Bruno", "Carla");

        MemberAging bruno = response.members().getFirst();
        assertThat(bruno.oldestSince()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(bruno.oldestDays()).isEqualTo(109);
        assertThat(bruno.buckets().get(AgingBucket.OVER_90_DAYS)).isEqualByComparingTo("40.00");
        assertThat(bruno.buckets().get(AgingBucket.FROM_31_TO_60_DAYS)).isEqualByComparingTo("10.00");
        assertThat(response.buckets().get(AgingBucket.UP_TO_30_DAYS)).isEqualByComparingTo("15.00");
    }

    @Test
    void settlementPaysOffTheOldestDebt() {
        expense(LocalDate.of(2026, 8, 10), 1L, "80.00", 1L, "40.00", 2L, "40.00");
        expense(LocalDate.of(2026, 11, 1), 1L, "20.00", 1L, "10.00", 2L, "10.00");
        settlement(LocalDate.of(2026, 11, 2), 2L, 1L, "45.00");

        MemberAging bruno = service.aging(1L, null).members().getFirst();

        assertThat(bruno.owed()).isEqualByComparingTo("5.00");
        assertThat(bruno.oldestSince()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(bruno.openDebts()).hasSize(1);
    }

    @Test
    void creditorsAndSettledMembersAreLeftOut() {
        expense(LocalDate.of(2026, 9, 1), 1L, "40.00", 1L, "20.00", 2L, "20.00");
        settlement(LocalDate.of(2026, 9, 3), 2L, 1L, "20.00");

        DebtAgingResponse response = service.aging(1L, null);

        assertThat(response.members()).isEmpty();
        assertThat(response.totalOwed()).isEqualByComparingTo("0.00");
        assertThat(response.buckets()).containsOnlyKeys(AgingBucket.values());
    }

    @Test
    void rebuildsThePositionOnAPastDate() {
        expense(LocalDate.of(2026, 9, 1), 1L, "40.00", 1L, "20.00", 2L, "20.00");
        settlement(LocalDate.of(2026, 10, 15), 2L, 1L, "20.00");

        DebtAgingResponse response = service.aging(1L, LocalDate.of(2026, 10, 1));

        assertThat(response.members()).singleElement().satisfies(bruno -> {
            assertThat(bruno.owed()).isEqualByComparingTo("20.00");
            assertThat(bruno.oldestDays()).isEqualTo(30);
        });
    }

    @Test
    void rejectsDateInTheFuture() {
        assertThatThrownBy(() -> service.aging(1L, TODAY.plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("futuro");
    }

    /** Owed alternates member id and amount, like {@link TestData#expense}. */
    private void expense(LocalDate spentAt, Long payerId, String amount, Object... owed) {
        Expense expense = TestData.expense(group, payerId, amount, owed);
        ReflectionTestUtils.setField(expense, "spentAt", spentAt);
        expenses.add(expense);
        expenses.sort((a, b) -> a.getSpentAt().compareTo(b.getSpentAt()));
    }

    private void settlement(LocalDate paidAt, Long fromId, Long toId, String amount) {
        Settlement settlement = TestData.settlement(group, fromId, toId, amount);
        ReflectionTestUtils.setField(settlement, "paidAt", paidAt);
        settlements.add(settlement);
    }
}
