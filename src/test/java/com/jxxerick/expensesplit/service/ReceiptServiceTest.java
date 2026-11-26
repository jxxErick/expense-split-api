package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.ReceiptItemRequest;
import com.jxxerick.expensesplit.dto.ReceiptSplitRequest;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse.MemberBillResponse;
import com.jxxerick.expensesplit.dto.ShareResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private ApplicationEventPublisher events;

    private ReceiptService receiptService;
    private Group group;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(expenseRepository, groupService, CLOCK, events);
        group = TestData.group(1L, "Rolê", "Ana", "Bruno", "Carla");
    }

    @Test
    void previewShowsEachMemberBillWithoutSaving() {
        when(groupService.getGroup(1L)).thenReturn(group);

        ReceiptSplitResponse preview = receiptService.preview(1L, dinner());

        assertThat(preview.total()).isEqualByComparingTo("91.20");
        assertThat(preview.members()).extracting(MemberBillResponse::name).containsExactly("Ana", "Bruno", "Carla");
        assertThat(preview.members()).extracting(MemberBillResponse::total)
                .containsExactly(new BigDecimal("31.72"), new BigDecimal("31.72"), new BigDecimal("27.76"));
        verifyNoInteractions(expenseRepository, events);
    }

    @Test
    void createsExactExpenseWithEachMemberTotal() {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = receiptService.create(1L, dinner());

        assertThat(response.amount()).isEqualByComparingTo("91.20");
        assertThat(response.splitType()).isEqualTo(SplitType.EXACT);
        assertThat(response.category()).isEqualTo(ExpenseCategory.FOOD);
        assertThat(response.spentAt()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(response.payer().name()).isEqualTo("Bruno");
        assertThat(response.shares()).extracting(ShareResponse::memberName, ShareResponse::amount).containsExactly(
                tuple("Ana", new BigDecimal("31.72")),
                tuple("Bruno", new BigDecimal("31.72")),
                tuple("Carla", new BigDecimal("27.76")));

        ArgumentCaptor<GroupActivityEvent> event = ArgumentCaptor.forClass(GroupActivityEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(ActivityType.EXPENSE_CREATED);
    }

    @Test
    void leavesOutWhoEndsUpOwingNothing() {
        when(groupService.getGroup(1L)).thenReturn(group);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = receiptService.create(1L, request(null, null,
                new ReceiptItemRequest("Bala", new BigDecimal("0.01"), null, List.of(1L, 2L)),
                new ReceiptItemRequest("Café", new BigDecimal("5.00"), null, List.of(1L))));

        assertThat(response.shares()).extracting(ShareResponse::memberName).containsExactly("Ana");
        assertThat(response.amount()).isEqualByComparingTo("5.01");
    }

    @Test
    void rejectsMemberWhoLeftTheGroup() {
        TestData.member(group, 3L).leave(CLOCK.instant());
        when(groupService.getGroup(1L)).thenReturn(group);

        assertThatThrownBy(() -> receiptService.create(1L, dinner()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Carla saiu do grupo");
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void rejectsUnknownMember() {
        when(groupService.getGroup(1L)).thenReturn(group);

        assertThatThrownBy(() -> receiptService.preview(1L, request(null, null,
                new ReceiptItemRequest("Prato", new BigDecimal("20.00"), 1, List.of(9L)))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Ana, Bruno and Carla share a pizza, Ana and Bruno two beers, Carla a juice; 10% service, R$ 10 off. */
    private static ReceiptSplitRequest dinner() {
        return request(new BigDecimal("10"), new BigDecimal("10.00"),
                new ReceiptItemRequest("Pizza", new BigDecimal("60.00"), 1, List.of(1L, 2L, 3L)),
                new ReceiptItemRequest("Cerveja", new BigDecimal("12.00"), 2, List.of(1L, 2L)),
                new ReceiptItemRequest("Suco", new BigDecimal("8.00"), null, List.of(3L)));
    }

    private static ReceiptSplitRequest request(BigDecimal servicePercent, BigDecimal discount, ReceiptItemRequest... items) {
        return new ReceiptSplitRequest("Pizzaria", 2L, null, null, List.of(items), servicePercent, discount);
    }
}
