package com.jxxerick.expensesplit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Money paid from one member to another to pay off debts. */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_member_id", nullable = false)
    private Member from;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_member_id", nullable = false)
    private Member to;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Set only when paid in a currency other than the group's; {@code amount} is then the converted value. */
    @Column(length = 3)
    private String originalCurrency;

    @Column(precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(precision = 15, scale = 6)
    private BigDecimal exchangeRate;

    protected Settlement() {
    }

    public Settlement(Group group, Member from, Member to, BigDecimal amount, LocalDate paidAt) {
        this.group = group;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.paidAt = paidAt;
        this.createdAt = Instant.now();
    }

    public void paidInForeignCurrency(String currency, BigDecimal foreignAmount, BigDecimal rate) {
        this.originalCurrency = currency;
        this.originalAmount = foreignAmount;
        this.exchangeRate = rate;
    }

    public boolean isForeign() {
        return originalCurrency != null;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Member getFrom() {
        return from;
    }

    public Member getTo() {
        return to;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
