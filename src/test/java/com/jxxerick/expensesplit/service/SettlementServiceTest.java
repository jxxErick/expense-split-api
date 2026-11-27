package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateSettlementRequest;
import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.SettlementResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.service.balance.DebtSimplifier;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private BalanceService balanceService;

    @Mock
    private ApplicationEventPublisher events;

    private SettlementService settlementService;
    private Group group;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(settlementRepository, groupService, balanceService,
                new DebtSimplifier(), CLOCK, events);
        group = TestData.group(1L, "Casa", "Ana", "Bruno", "Carla");
        when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void recordsSettlementDatedToday() {
        givenBalances("60.00", "-10.00", "-50.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementResponse response = settlementService.create(1L, request(3L, 1L, "50.00"));

        assertThat(response.from().name()).isEqualTo("Carla");
        assertThat(response.to().name()).isEqualTo("Ana");
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.paidAt()).isEqualTo(LocalDate.of(2026, 11, 17));
        verify(events).publishEvent(new GroupActivityEvent(1L, ActivityType.SETTLEMENT_RECORDED,
                "Carla pagou R$ 50.00 para Ana", new BigDecimal("50.00"), null));
    }

    @Test
    void allowsPartialPayment() {
        givenBalances("60.00", "-10.00", "-50.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(settlementService.create(1L, request(3L, 1L, "20.00")).amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void rejectsAmountAboveWhatPayerOwes() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, request(2L, 1L, "10.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O valor máximo desse acerto é R$ 10.00");
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void rejectsPayerWithoutDebts() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, request(1L, 2L, "5.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Ana não tem dívidas no grupo");
    }

    @Test
    void rejectsReceiverWhoIsNotOwed() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, request(3L, 2L, "5.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bruno não tem valores a receber no grupo");
    }

    @Test
    void rejectsPayingYourself() {
        assertThatThrownBy(() -> settlementService.create(1L, request(2L, 2L, "5.00")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMemberOutsideGroup() {
        assertThatThrownBy(() -> settlementService.create(1L, request(2L, 77L, "5.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void settleUpRecordsEverySimplifiedTransfer() {
        givenBalances("60.00", "-10.00", "-50.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SettlementResponse> settled = settlementService.settleUp(1L, null);

        assertThat(settled).extracting(s -> s.from().name(), s -> s.to().name(), s -> s.amount().toPlainString(),
                        SettlementResponse::paidAt)
                .containsExactly(
                        tuple("Carla", "Ana", "50.00", LocalDate.of(2026, 11, 17)),
                        tuple("Bruno", "Ana", "10.00", LocalDate.of(2026, 11, 17)));
        verify(events, times(2)).publishEvent(any(GroupActivityEvent.class));
    }

    @Test
    void settleUpUsesGivenDate() {
        givenBalances("10.00", "-10.00", "0.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SettlementResponse> settled = settlementService.settleUp(1L, LocalDate.of(2026, 11, 1));

        assertThat(settled).singleElement().extracting(SettlementResponse::paidAt)
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void settleUpRejectsGroupAlreadySettled() {
        givenBalances("0.00", "0.00", "0.00");

        assertThatThrownBy(() -> settlementService.settleUp(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Todos os saldos do grupo já estão zerados");
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void convertsForeignSettlementIntoGroupCurrency() {
        givenBalances("60.00", "-10.00", "-50.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementResponse response = settlementService.create(1L, foreign(3L, 1L, "5.00", "usd", "5.40"));

        assertThat(response.amount()).isEqualByComparingTo("27.00");
        assertThat(response.foreign()).isEqualTo(new ExpenseResponse.ForeignAmountResponse("USD",
                new BigDecimal("5.00"), new BigDecimal("5.40")));
        verify(events).publishEvent(new GroupActivityEvent(1L, ActivityType.SETTLEMENT_RECORDED,
                "Carla pagou US$ 5.00 (R$ 27.00) para Ana", new BigDecimal("27.00"), null));
    }

    @Test
    void foreignAmountOneCentAwayPaysTheDebtOffExactly() {
        givenBalances("60.00", "-10.00", "-50.00");
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        // 9.31 * 5.37 = 49.99 and 9.32 * 5.37 = 50.05: neither is 50.00, both are within one US cent
        assertThat(settlementService.create(1L, foreign(3L, 1L, "9.31", "USD", "5.37")).amount())
                .isEqualByComparingTo("50.00");
        assertThat(settlementService.create(1L, foreign(3L, 1L, "9.32", "USD", "5.37")).amount())
                .isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsForeignAmountAboveDebtShowingBothLimits() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, foreign(2L, 1L, "3.00", "USD", "5.40")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O valor máximo desse acerto é R$ 10.00 (US$ 1.85 nessa cotação)");
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void requiresRateForForeignCurrency() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, foreign(3L, 1L, "5.00", "EUR", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Informe a cotação de EUR para BRL");
    }

    @Test
    void rejectsRateForGroupCurrency() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, foreign(3L, 1L, "5.00", "brl", "1.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A cotação só vale para acertos em outra moeda");
    }

    @Test
    void rejectsUnknownCurrency() {
        givenBalances("60.00", "-10.00", "-50.00");

        assertThatThrownBy(() -> settlementService.create(1L, foreign(3L, 1L, "5.00", "XYZ", "1.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Moeda desconhecida: XYZ");
    }

    private static CreateSettlementRequest foreign(Long from, Long to, String amount, String currency, String rate) {
        return new CreateSettlementRequest(from, to, new BigDecimal(amount), null, currency,
                rate == null ? null : new BigDecimal(rate));
    }

    private void givenBalances(String ana, String bruno, String carla) {
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        balances.put(1L, new BigDecimal(ana));
        balances.put(2L, new BigDecimal(bruno));
        balances.put(3L, new BigDecimal(carla));
        when(balanceService.netBalances(group)).thenReturn(balances);
    }

    private static CreateSettlementRequest request(Long from, Long to, String amount) {
        return new CreateSettlementRequest(from, to, new BigDecimal(amount), null);
    }
}
