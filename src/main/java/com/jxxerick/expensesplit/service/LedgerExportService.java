package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.Settlement;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import com.jxxerick.expensesplit.repository.SettlementRepository;
import com.jxxerick.expensesplit.service.export.CsvWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole group as a spreadsheet: one line per expense or settlement, oldest first, and one
 * column per member with how that line moved their balance. The last line adds the columns up,
 * which gives the same balances as {@link BalanceService}.
 */
@Service
public class LedgerExportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Map<ExpenseCategory, String> CATEGORY_LABELS = Map.of(
            ExpenseCategory.FOOD, "Alimentação",
            ExpenseCategory.GROCERIES, "Mercado",
            ExpenseCategory.TRANSPORT, "Transporte",
            ExpenseCategory.LODGING, "Hospedagem",
            ExpenseCategory.LEISURE, "Lazer",
            ExpenseCategory.BILLS, "Contas",
            ExpenseCategory.OTHER, "Outros");

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public LedgerExportService(GroupService groupService, ExpenseRepository expenseRepository,
                               SettlementRepository settlementRepository) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long groupId) {
        Group group = groupService.getGroup(groupId);
        List<Member> members = group.getMembers();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        members.forEach(member -> totals.put(member.getId(), ZERO));

        CsvWriter csv = new CsvWriter();
        List<String> header = new ArrayList<>(List.of("data", "tipo", "descrição", "categoria", "pago por",
                "valor (" + group.getCurrency() + ")", "moeda original", "valor original", "cotação"));
        members.forEach(member -> header.add(CsvWriter.text(member.getName())));
        csv.row(header);

        for (Line line : linesOf(group)) {
            List<String> cells = new ArrayList<>(line.cells());
            for (Member member : members) {
                BigDecimal effect = line.effects().get(member.getId());
                cells.add(CsvWriter.number(effect));
                if (effect != null) {
                    totals.merge(member.getId(), effect, BigDecimal::add);
                }
            }
            csv.row(cells);
        }

        List<String> footer = new ArrayList<>(List.of("", "Saldo", "", "", "", "", "", "", ""));
        totals.values().forEach(total -> footer.add(CsvWriter.number(total)));
        return csv.row(footer).toString();
    }

    /** Oldest first; on the same day expenses come before settlements, like the member statement. */
    private List<Line> linesOf(Group group) {
        List<Line> lines = new ArrayList<>();
        for (Expense expense : expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(group.getId()).reversed()) {
            lines.add(expenseLine(expense));
        }
        for (Settlement settlement : settlementRepository.findByGroupIdOrderByPaidAtDescIdDesc(group.getId()).reversed()) {
            lines.add(settlementLine(settlement));
        }
        // stable sort keeps expenses ahead of settlements on the same day
        lines.sort(Comparator.comparing(Line::date));
        return lines;
    }

    /** Payer gets the amount, everyone with a share loses it: paid - share. */
    private static Line expenseLine(Expense expense) {
        Map<Long, BigDecimal> effects = new LinkedHashMap<>();
        effects.put(expense.getPayer().getId(), expense.getAmount());
        for (ExpenseShare share : expense.getShares()) {
            effects.merge(share.getMember().getId(), share.getAmount().negate(), BigDecimal::add);
        }
        List<String> cells = List.of(
                expense.getSpentAt().toString(),
                "Despesa",
                CsvWriter.text(expense.getDescription()),
                CATEGORY_LABELS.get(expense.getCategory()),
                CsvWriter.text(expense.getPayer().getName()),
                CsvWriter.number(expense.getAmount()),
                expense.isForeign() ? expense.getOriginalCurrency() : "",
                CsvWriter.number(expense.getOriginalAmount()),
                rate(expense.getExchangeRate()));
        return new Line(expense.getSpentAt(), cells, effects);
    }

    /** Paying a debt moves the payer up and the receiver down: + sent - received. */
    private static Line settlementLine(Settlement settlement) {
        Map<Long, BigDecimal> effects = Map.of(
                settlement.getFrom().getId(), settlement.getAmount(),
                settlement.getTo().getId(), settlement.getAmount().negate());
        List<String> cells = List.of(
                settlement.getPaidAt().toString(),
                "Acerto",
                CsvWriter.text(settlement.getFrom().getName() + " pagou " + settlement.getTo().getName()),
                "",
                CsvWriter.text(settlement.getFrom().getName()),
                CsvWriter.number(settlement.getAmount()),
                settlement.isForeign() ? settlement.getOriginalCurrency() : "",
                CsvWriter.number(settlement.getOriginalAmount()),
                rate(settlement.getExchangeRate()));
        return new Line(settlement.getPaidAt(), cells, effects);
    }

    /** The database keeps six decimals; "5.400000" reads better as "5,4". */
    private static String rate(BigDecimal rate) {
        return CsvWriter.number(rate == null ? null : rate.stripTrailingZeros());
    }

    /** First nine cells, plus the balance effect per member id (absent: not involved). */
    private record Line(LocalDate date, List<String> cells, Map<Long, BigDecimal> effects) {
    }
}
