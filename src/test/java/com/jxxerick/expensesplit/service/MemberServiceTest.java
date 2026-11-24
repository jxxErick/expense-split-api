package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.RecurrenceFrequency;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringShare;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.RecurringExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Mock
    private GroupService groupService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private ApplicationEventPublisher events;

    private MemberService memberService;
    private Group group;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(groupService, balanceService, recurringExpenseRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), events);
        group = TestData.group(1L, "Casa", "Ana", "Bruno", "Carla");
        when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void memberWithZeroBalanceLeaves() {
        givenBalances("10.00", "-10.00", "0.00");
        when(recurringExpenseRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of());

        memberService.leave(1L, 3L);

        Member carla = TestData.member(group, 3L);
        assertThat(carla.isActive()).isFalse();
        assertThat(carla.getLeftAt()).isEqualTo(NOW);
        assertThat(group.activeMemberCount()).isEqualTo(2);
        verify(events).publishEvent(new GroupActivityEvent(1L, ActivityType.MEMBER_LEFT, "Carla saiu do grupo",
                null, 3L));
    }

    @Test
    void debtorCannotLeave() {
        givenBalances("10.00", "-10.00", "0.00");

        assertThatThrownBy(() -> memberService.leave(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bruno ainda deve R$ 10.00; acerte as contas antes de sair");
        assertThat(TestData.member(group, 2L).isActive()).isTrue();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void creditorCannotLeave() {
        givenBalances("10.00", "-10.00", "0.00");

        assertThatThrownBy(() -> memberService.leave(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ana ainda tem a receber R$ 10.00; acerte as contas antes de sair");
    }

    @Test
    void memberInLiveRecurringExpenseCannotLeave() {
        givenBalances("0.00", "0.00", "0.00");
        RecurringExpense rent = new RecurringExpense(group, "Aluguel", new BigDecimal("900.00"),
                TestData.member(group, 1L), SplitType.EQUAL, ExpenseCategory.BILLS, RecurrenceFrequency.MONTHLY,
                LocalDate.of(2026, 11, 1), null, List.of(new RecurringShare(1L, null), new RecurringShare(3L, null)));
        when(recurringExpenseRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(rent));

        assertThatThrownBy(() -> memberService.leave(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Carla participa da despesa recorrente 'Aluguel'");
    }

    @Test
    void memberCannotLeaveTwice() {
        TestData.member(group, 3L).leave(NOW);

        assertThatThrownBy(() -> memberService.leave(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Carla já saiu do grupo");
    }

    @Test
    void lastActiveMemberCannotLeave() {
        TestData.member(group, 2L).leave(NOW);
        TestData.member(group, 3L).leave(NOW);

        assertThatThrownBy(() -> memberService.leave(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O grupo precisa de pelo menos um membro");
    }

    @Test
    void unknownMemberIs404() {
        assertThatThrownBy(() -> memberService.leave(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenBalances(String ana, String bruno, String carla) {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal(ana));
        balances.put(2L, new BigDecimal(bruno));
        balances.put(3L, new BigDecimal(carla));
        when(balanceService.netBalances(group)).thenReturn(balances);
    }
}
