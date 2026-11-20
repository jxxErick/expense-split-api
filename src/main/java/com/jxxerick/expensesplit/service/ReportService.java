package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.SpendingReportResponse;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.CategoryTotal;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MemberSpending;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MonthlyTotal;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.ExpenseShare;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GroupService groupService;
    private final ExpenseRepository expenseRepository;

    public ReportService(GroupService groupService, ExpenseRepository expenseRepository) {
        this.groupService = groupService;
        this.expenseRepository = expenseRepository;
    }

    /** Both bounds are optional and inclusive. */
    @Transactional(readOnly = true)
    public SpendingReportResponse spending(Long groupId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("A data inicial deve ser anterior ou igual à final");
        }
        Group group = groupService.getGroup(groupId);
        List<Expense> expenses = expenseRepository.findByGroupIdOrderBySpentAtDescIdDesc(groupId).stream()
                .filter(e -> from == null || !e.getSpentAt().isBefore(from))
                .filter(e -> to == null || !e.getSpentAt().isAfter(to))
                .toList();

        Totals overall = new Totals();
        Map<YearMonth, Totals> months = new TreeMap<>();
        Map<ExpenseCategory, Totals> categories = new EnumMap<>(ExpenseCategory.class);
        Map<Long, Totals> paid = new LinkedHashMap<>();
        Map<Long, Totals> consumed = new LinkedHashMap<>();
        for (Member member : group.getMembers()) {
            paid.put(member.getId(), new Totals());
            consumed.put(member.getId(), new Totals());
        }

        for (Expense expense : expenses) {
            overall.add(expense.getAmount());
            months.computeIfAbsent(YearMonth.from(expense.getSpentAt()), k -> new Totals()).add(expense.getAmount());
            categories.computeIfAbsent(expense.getCategory(), k -> new Totals()).add(expense.getAmount());
            paid.get(expense.getPayer().getId()).add(expense.getAmount());
            for (ExpenseShare share : expense.getShares()) {
                consumed.get(share.getMember().getId()).add(share.getAmount());
            }
        }

        List<MonthlyTotal> monthly = months.entrySet().stream()
                .map(e -> new MonthlyTotal(e.getKey().toString(), e.getValue().sum, e.getValue().count))
                .toList();
        List<CategoryTotal> byCategory = categories.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().sum, e.getValue().count,
                        percentage(e.getValue().sum, overall.sum)))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
        List<MemberSpending> members = group.getMembers().stream()
                .map(m -> new MemberSpending(m.getId(), m.getName(), paid.get(m.getId()).sum,
                        consumed.get(m.getId()).sum, percentage(consumed.get(m.getId()).sum, overall.sum)))
                .toList();

        return new SpendingReportResponse(overall.sum, overall.count, overall.average(), monthly, byCategory, members);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? ZERO : part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_EVEN);
    }

    /** Running sum and count. */
    private static final class Totals {
        private BigDecimal sum = ZERO;
        private int count;

        private void add(BigDecimal amount) {
            sum = sum.add(amount);
            count++;
        }

        private BigDecimal average() {
            return count == 0 ? ZERO : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_EVEN);
        }
    }
}
