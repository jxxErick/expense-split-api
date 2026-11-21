package com.jxxerick.expensesplit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/** Participant template of a recurring expense; {@code value} follows the split type rules. */
@Embeddable
public class RecurringShare {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "share_value", precision = 12, scale = 2)
    private BigDecimal value;

    protected RecurringShare() {
    }

    public RecurringShare(Long memberId, BigDecimal value) {
        this.memberId = memberId;
        this.value = value;
    }

    public Long getMemberId() {
        return memberId;
    }

    public BigDecimal getValue() {
        return value;
    }
}
