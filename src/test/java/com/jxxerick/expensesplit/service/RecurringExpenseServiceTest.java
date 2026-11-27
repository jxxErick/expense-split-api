package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateRecurringExpenseRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.RecurringExpenseResponse;
import com.jxxerick.expensesplit.dto.ShareRequest;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.RecurrenceFrequency;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringShare;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.RecurringExpenseRepository;
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
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RecurringExpenseRepository recurringRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private ApplicationEventPublisher events;

    private RecurringExpenseService service;
    private Group group;

    @BeforeEach
    void setUp() {
        SplitCalculator calculator = new SplitCalculator(List.of(
                new EqualSplitStrategy(), new ExactSplitStrategy(), new PercentageSplitStrategy(), new SharesSplitStrategy()));
        ExpenseService expenseService = new ExpenseService(expenseRepository, groupService, calculator, CLOCK, events);
        service = new RecurringExpenseService(recurringRepository, groupService, expenseService, CLOCK);
        group = TestData.group(1L, "Casa", "Ana", "Bruno", "Carla");
        lenient().when(groupService.getGroup(1L)).thenReturn(group);
        lenient().when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsTemplateWithParticipantNames() {
        when(recurringRepository.save(any(RecurringExpense.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringExpenseResponse response = service.create(1L, request(TODAY, null,
                share(1L, "50"), share(2L, "30"), share(3L, "20")));

        assertThat(response.status()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(response.nextRunOn()).isEqualTo(TODAY);
        assertThat(response.category()).isEqualTo(ExpenseCategory.BILLS);
        assertThat(response.participants()).extracting(RecurringExpenseResponse.Participant::memberName)
                .containsExactly("Ana", "Bruno", "Carla");
    }

    @Test
    void rejectsInvalidSplitBeforeSaving() {
        assertThatThrownBy(() -> service.create(1L, request(TODAY, null, share(1L, "50"), share(2L, "30"))))
                .isInstanceOf(BusinessException.class);
        verify(recurringRepository, never()).save(any());
    }

    @Test
    void rejectsEndDateBeforeStart() {
        assertThatThrownBy(() -> service.create(1L, request(TODAY, TODAY.minusDays(1), share(1L, "100"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("data final");
    }

    @Test
    void runCatchesUpEveryMissedOccurrence() {
        RecurringExpense rent = recurring(LocalDate.of(2026, 9, 21));
        when(recurringRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(rent));

        List<ExpenseResponse> created = service.runDue(1L);

        assertThat(created).extracting(ExpenseResponse::spentAt).containsExactly(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 21));
        assertThat(created).allSatisfy(e -> assertThat(e.amount()).isEqualByComparingTo("900.00"));
        assertThat(rent.getNextRunOn()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(service.runDue(1L)).isEmpty();
    }

    @Test
    void catchUpIsCappedPerRun() {
        RecurringExpense weekly = new RecurringExpense(group, "Faxina", new BigDecimal("90.00"),
                TestData.member(group, 1L), SplitType.EQUAL, ExpenseCategory.OTHER, RecurrenceFrequency.WEEKLY,
                TODAY.minusWeeks(40), null, List.of(new RecurringShare(1L, null)));
        when(recurringRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(weekly));

        assertThat(service.runDue(1L)).hasSize(RecurringExpenseService.MAX_CATCH_UP);
        assertThat(weekly.isDue(TODAY)).isTrue();
    }

    @Test
    void pausedTemplatesAreNotGenerated() {
        RecurringExpense rent = recurring(TODAY);
        rent.pause();
        when(recurringRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(rent));

        assertThat(service.runDue(1L)).isEmpty();
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void scheduledRunCountsCreatedExpenses() {
        when(recurringRepository.findByStatusAndNextRunOnLessThanEqualOrderByIdAsc(RecurringStatus.ACTIVE, TODAY))
                .thenReturn(List.of(recurring(TODAY.minusMonths(1)), recurring(TODAY)));

        assertThat(service.generateAllDue()).isEqualTo(3);
    }

    @Test
    void cannotResumeFinishedTemplate() {
        RecurringExpense finished = new RecurringExpense(group, "Curso", new BigDecimal("100.00"),
                TestData.member(group, 1L), SplitType.EQUAL, ExpenseCategory.OTHER, RecurrenceFrequency.MONTHLY,
                TODAY, TODAY, List.of(new RecurringShare(1L, null)));
        finished.advance();
        when(recurringRepository.findByIdAndGroupId(4L, 1L)).thenReturn(Optional.of(finished));

        assertThatThrownBy(() -> service.resume(1L, 4L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void createsTemplateInForeignCurrency() {
        when(recurringRepository.save(any(RecurringExpense.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringExpenseResponse response = service.create(1L, foreignRequest("usd", new BigDecimal("5.25")));

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.amount()).isEqualByComparingTo("15.99");
        assertThat(response.convertedAmount()).isEqualByComparingTo("83.95");
    }

    @Test
    void foreignTemplateRequiresExchangeRate() {
        assertThatThrownBy(() -> service.create(1L, foreignRequest("USD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cotação");
        verify(recurringRepository, never()).save(any());
    }

    @Test
    void generatedOccurrencesAreConvertedWithTheCurrentRate() {
        RecurringExpense streaming = streaming(TODAY.minusMonths(1), "5.00");
        when(recurringRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(streaming));

        List<ExpenseResponse> created = service.runDue(1L);

        assertThat(created).hasSize(2).allSatisfy(expense -> {
            assertThat(expense.amount()).isEqualByComparingTo("79.95");
            assertThat(expense.foreign().currency()).isEqualTo("USD");
            assertThat(expense.foreign().amount()).isEqualByComparingTo("15.99");
        });
    }

    @Test
    void changingTheRateOnlyAffectsNextOccurrences() {
        RecurringExpense streaming = streaming(TODAY, "5.00");
        when(recurringRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(List.of(streaming));
        when(recurringRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(streaming));
        ExpenseResponse before = service.runDue(1L).getFirst();

        RecurringExpenseResponse response = service.changeExchangeRate(1L, 5L, new BigDecimal("6.00"));

        assertThat(before.amount()).isEqualByComparingTo("79.95");
        assertThat(response.exchangeRate()).isEqualByComparingTo("6.00");
        assertThat(response.convertedAmount()).isEqualByComparingTo("95.94");
    }

    @Test
    void cannotChangeRateOfTemplateInGroupCurrency() {
        when(recurringRepository.findByIdAndGroupId(4L, 1L)).thenReturn(Optional.of(recurring(TODAY)));

        assertThatThrownBy(() -> service.changeExchangeRate(1L, 4L, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("moeda do grupo");
    }

    @Test
    void rejectsRateThatConvertsBelowOneCent() {
        when(recurringRepository.findByIdAndGroupId(5L, 1L)).thenReturn(Optional.of(streaming(TODAY, "5.00")));

        assertThatThrownBy(() -> service.changeExchangeRate(1L, 5L, new BigDecimal("0.000001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("centavo");
    }

    @Test
    void deleteFailsWhenTemplateIsFromAnotherGroup() {
        when(recurringRepository.findByIdAndGroupId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private RecurringExpense recurring(LocalDate startOn) {
        return new RecurringExpense(group, "Aluguel", new BigDecimal("900.00"), TestData.member(group, 1L),
                SplitType.EQUAL, ExpenseCategory.BILLS, RecurrenceFrequency.MONTHLY, startOn, null,
                List.of(new RecurringShare(1L, null), new RecurringShare(2L, null), new RecurringShare(3L, null)));
    }

    private RecurringExpense streaming(LocalDate startOn, String rate) {
        RecurringExpense streaming = new RecurringExpense(group, "Streaming", new BigDecimal("15.99"),
                TestData.member(group, 1L), SplitType.EQUAL, ExpenseCategory.LEISURE, RecurrenceFrequency.MONTHLY,
                startOn, null,
                List.of(new RecurringShare(1L, null), new RecurringShare(2L, null), new RecurringShare(3L, null)));
        streaming.chargeInForeignCurrency("USD", new BigDecimal(rate));
        return streaming;
    }

    private static CreateRecurringExpenseRequest foreignRequest(String currency, BigDecimal rate) {
        return new CreateRecurringExpenseRequest("Streaming", new BigDecimal("15.99"), 1L, SplitType.EQUAL,
                ExpenseCategory.LEISURE, RecurrenceFrequency.MONTHLY, TODAY, null,
                List.of(new ShareRequest(1L, null), new ShareRequest(2L, null), new ShareRequest(3L, null)), currency, rate);
    }

    private static CreateRecurringExpenseRequest request(LocalDate startOn, LocalDate endOn, ShareRequest... shares) {
        return new CreateRecurringExpenseRequest(" Aluguel ", new BigDecimal("1500.00"), 1L, SplitType.PERCENTAGE,
                ExpenseCategory.BILLS, RecurrenceFrequency.MONTHLY, startOn, endOn, List.of(shares));
    }

    private static ShareRequest share(Long memberId, String value) {
        return new ShareRequest(memberId, new BigDecimal(value));
    }
}
