package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Activity;
import com.jxxerick.expensesplit.model.ActivityType;

import java.math.BigDecimal;
import java.time.Instant;

public record ActivityResponse(Long id, ActivityType type, String summary, BigDecimal amount, Long referenceId,
                               Instant occurredAt) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(activity.getId(), activity.getType(), activity.getSummary(), activity.getAmount(),
                activity.getReferenceId(), activity.getOccurredAt());
    }
}
