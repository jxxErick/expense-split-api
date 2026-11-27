package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.RecurrenceFrequency;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.service.currency.CurrencyConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code amount} is in {@code currency}; {@code convertedAmount} is what the next occurrence adds to the
 * group, in the group currency ({@code exchangeRate} is null when both currencies are the same).
 */
public record RecurringExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount,
        MemberResponse payer,
        SplitType splitType,
        ExpenseCategory category,
        RecurrenceFrequency frequency,
        LocalDate startOn,
        LocalDate endOn,
        RecurringStatus status,
        LocalDate nextRunOn,
        int generatedCount,
        List<Participant> participants
) {

    public static RecurringExpenseResponse from(RecurringExpense recurring) {
        List<Participant> participants = recurring.getShares().stream()
                .map(share -> new Participant(share.getMemberId(),
                        recurring.getGroup().findMember(share.getMemberId()).map(Member::getName).orElse(null),
                        share.getValue()))
                .toList();
        boolean finished = recurring.getStatus() == RecurringStatus.FINISHED;
        boolean foreign = recurring.isForeign();
        return new RecurringExpenseResponse(
                recurring.getId(),
                recurring.getDescription(),
                recurring.getAmount(),
                foreign ? recurring.getCurrency() : recurring.getGroup().getCurrency(),
                recurring.getExchangeRate(),
                foreign ? CurrencyConverter.convert(recurring.getAmount(), recurring.getExchangeRate())
                        : recurring.getAmount(),
                MemberResponse.from(recurring.getPayer()),
                recurring.getSplitType(),
                recurring.getCategory(),
                recurring.getFrequency(),
                recurring.getStartOn(),
                recurring.getEndOn(),
                recurring.getStatus(),
                finished ? null : recurring.getNextRunOn(),
                recurring.getOccurrences(),
                participants);
    }

    public record Participant(Long memberId, String memberName, BigDecimal value) {
    }
}
