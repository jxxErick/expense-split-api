package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.BalanceResponse;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.model.SplitType;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerExportServiceTest {

    @Mock
    private GroupService groupService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    private LedgerExportService exportService;
    private Group group;

    @BeforeEach
    void setUp() {
        exportService = new LedgerExportService(groupService, expenseRepository, settlementRepository);
        group = TestData.group(1L, "Viagem", "Ana", "Bruno", "Carla");
        when(groupService.getGroup(1L)).thenReturn(group);
    }

    @Test
    void writesOneLinePerMovementOldestFirstWithBalanceColumns() {
        Expense dinner = expense("Jantar; com vinho", "90.00", 1L, LocalDate.of(2026, 11, 10),
                1L, "30.00", 2L, "30.00", 3L, "30.00");
        Expense taxi = expense("Táxi", "20.00", 2L, LocalDate.of(2026, 11, 12), 2L, "10.00", 3L, "10.00");
        Settlement payback = new Settlement(group, TestData.member(group, 3L), TestData.member(group, 1L),
                new BigDecimal("30.00"), LocalDate.of(2026, 11, 12));
        // repositories return newest first
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(List.of(taxi, dinner));
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(List.of(payback));

        List<String> lines = exportService.exportCsv(1L).lines().toList();

        assertThat(lines).containsExactly(
                "data;tipo;descrição;categoria;pago por;valor (BRL);moeda original;valor original;cotação;Ana;Bruno;Carla",
                "2026-11-10;Despesa;\"Jantar; com vinho\";Alimentação;Ana;90,00;;;;60,00;-30,00;-30,00",
                "2026-11-12;Despesa;Táxi;Transporte;Bruno;20,00;;;;;10,00;-10,00",
                "2026-11-12;Acerto;Carla pagou Ana;;Carla;30,00;;;;-30,00;;30,00",
                ";Saldo;;;;;;;;30,00;-20,00;-10,00");
    }

    @Test
    void footerMatchesTheBalanceService() {
        Expense hotel = expense("Hotel", "543.21", 2L, LocalDate.of(2026, 11, 1), 1L, "181.07", 2L, "181.07",
                3L, "181.07");
        hotel.paidInForeignCurrency("USD", new BigDecimal("100.00"), new BigDecimal("5.4321"));
        Settlement settlement = new Settlement(group, TestData.member(group, 1L), TestData.member(group, 2L),
                new BigDecimal("50.00"), LocalDate.of(2026, 11, 2));
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(List.of(hotel));
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(List.of(settlement));

        List<String> lines = exportService.exportCsv(1L).lines().toList();
        List<BalanceResponse> balances = new BalanceService(groupService, expenseRepository, settlementRepository,
                new DebtSimplifier()).balances(1L);

        assertThat(lines.get(1)).isEqualTo(
                "2026-11-01;Despesa;Hotel;Outros;Bruno;543,21;USD;100,00;5,4321;-181,07;362,14;-181,07");
        String expectedFooter = ";Saldo;;;;;;;;" + String.join(";", balances.stream()
                .map(b -> b.balance().toPlainString().replace('.', ',')).toList());
        assertThat(lines.getLast()).isEqualTo(expectedFooter);
    }

    @Test
    void protectsNamesAndDescriptionsFromFormulaInjection() {
        Expense sneaky = expense("=HYPERLINK(\"http://evil\")", "10.00", 1L, LocalDate.of(2026, 11, 1),
                1L, "10.00");
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(List.of(sneaky));
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(List.of());

        assertThat(exportService.exportCsv(1L).lines().toList().get(1))
                .startsWith("2026-11-01;Despesa;\"'=HYPERLINK(\"\"http://evil\"\")\";");
    }

    @Test
    void emptyGroupHasHeaderAndZeroBalances() {
        when(expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(1L)).thenReturn(List.of());
        when(settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(1L)).thenReturn(List.of());

        assertThat(exportService.exportCsv(1L).lines().toList()).hasSize(2).last()
                .isEqualTo(";Saldo;;;;;;;;0,00;0,00;0,00");
    }

    /** {@code owed} alternates member id and amount. */
    private Expense expense(String description, String amount, Long payerId, LocalDate date, Object... owed) {
        ExpenseCategory category = switch (description) {
            case "Jantar; com vinho" -> ExpenseCategory.FOOD;
            case "Táxi" -> ExpenseCategory.TRANSPORT;
            default -> ExpenseCategory.OTHER;
        };
        Expense expense = new Expense(group, description, new BigDecimal(amount), TestData.member(group, payerId),
                SplitType.EXACT, category, date);
        for (int i = 0; i < owed.length; i += 2) {
            expense.addShare(TestData.member(group, (Long) owed[i]), new BigDecimal((String) owed[i + 1]));
        }
        return expense;
    }
}
