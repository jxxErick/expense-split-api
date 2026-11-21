package com.jxxerick.expensesplit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable entry of a group's history. Keeps only ids and a ready-made summary, so it
 * survives the deletion of the expense it talks about.
 */
@Entity
@Table(name = "activities", indexes = @Index(name = "ix_activities_group_id", columnList = "group_id, id"))
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    /** Id of the expense, settlement or member involved, when there is one. */
    private Long referenceId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected Activity() {
    }

    public Activity(Long groupId, ActivityType type, String summary, BigDecimal amount, Long referenceId,
                    Instant occurredAt) {
        this.groupId = groupId;
        this.type = type;
        this.summary = summary;
        this.amount = amount;
        this.referenceId = referenceId;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public ActivityType getType() {
        return type;
    }

    public String getSummary() {
        return summary;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
