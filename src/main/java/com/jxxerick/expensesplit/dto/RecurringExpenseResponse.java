package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.model.RecurrenceFrequency;
import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.model.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecurringExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
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
        return new RecurringExpenseResponse(
                recurring.getId(),
                recurring.getDescription(),
                recurring.getAmount(),
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
