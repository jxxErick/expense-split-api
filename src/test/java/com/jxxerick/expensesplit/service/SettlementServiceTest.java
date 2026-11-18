package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.CreateSettlementRequest;
import com.jxxerick.expensesplit.dto.SettlementResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.SettlementRepository;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private SettlementService settlementService;
    private Group group;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(settlementRepository, groupService, balanceService, CLOCK);
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
