package com.jxxerick.expensesplit.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Template that generates a regular expense (rent, internet, streaming) on every occurrence. */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private Member payer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurrenceFrequency frequency;

    @Column(nullable = false)
    private LocalDate startOn;

    private LocalDate endOn;

    /** Occurrences already generated or skipped; the next one is {@code frequency.occurrence(startOn, occurrences)}. */
    @Column(nullable = false)
    private int occurrences;

    @Column(nullable = false)
    private LocalDate nextRunOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecurringStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "recurring_expense_shares", joinColumns = @JoinColumn(name = "recurring_expense_id"))
    @OrderColumn(name = "position")
    private List<RecurringShare> shares = new ArrayList<>();

    protected RecurringExpense() {
    }

    @SuppressWarnings("java:S107")
    public RecurringExpense(Group group, String description, BigDecimal amount, Member payer, SplitType splitType,
                            ExpenseCategory category, RecurrenceFrequency frequency, LocalDate startOn,
                            LocalDate endOn, List<RecurringShare> shares) {
        this.group = group;
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.splitType = splitType;
        this.category = category;
        this.frequency = frequency;
        this.startOn = startOn;
        this.endOn = endOn;
        this.shares = new ArrayList<>(shares);
        this.status = RecurringStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.nextRunOn = startOn;
        finishIfPastEnd();
    }

    /** True when an occurrence is waiting to be generated on or before {@code today}. */
    public boolean isDue(LocalDate today) {
        return status == RecurringStatus.ACTIVE && !nextRunOn.isAfter(today);
    }

    /** Marks the current occurrence as generated and returns its date. */
    public LocalDate advance() {
        LocalDate current = nextRunOn;
        occurrences++;
        nextRunOn = frequency.occurrence(startOn, occurrences);
        finishIfPastEnd();
        return current;
    }

    public void pause() {
        if (status == RecurringStatus.ACTIVE) {
            status = RecurringStatus.PAUSED;
        }
    }

    /** Resumes without backfilling the occurrences missed while paused. */
    public void resume(LocalDate today) {
        if (status != RecurringStatus.PAUSED) {
            return;
        }
        status = RecurringStatus.ACTIVE;
        while (nextRunOn.isBefore(today)) {
            occurrences++;
            nextRunOn = frequency.occurrence(startOn, occurrences);
        }
        finishIfPastEnd();
    }

    private void finishIfPastEnd() {
        if (endOn != null && nextRunOn.isAfter(endOn)) {
            status = RecurringStatus.FINISHED;
        }
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Member getPayer() {
        return payer;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public LocalDate getStartOn() {
        return startOn;
    }

    public LocalDate getEndOn() {
        return endOn;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public LocalDate getNextRunOn() {
        return nextRunOn;
    }

    public RecurringStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RecurringShare> getShares() {
        return List.copyOf(shares);
    }
}
