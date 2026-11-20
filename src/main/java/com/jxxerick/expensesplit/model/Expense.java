package com.jxxerick.expensesplit.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
public class Expense {

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

    @Column(nullable = false)
    private LocalDate spentAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ExpenseShare> shares = new ArrayList<>();

    protected Expense() {
    }

    public Expense(Group group, String description, BigDecimal amount, Member payer,
                   SplitType splitType, ExpenseCategory category, LocalDate spentAt) {
        this.group = group;
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.splitType = splitType;
        this.category = category;
        this.spentAt = spentAt;
        this.createdAt = Instant.now();
    }

    public void addShare(Member member, BigDecimal shareAmount) {
        shares.add(new ExpenseShare(this, member, shareAmount));
    }

    /** Replaces the details; shares must be re-added afterwards. */
    public void update(String description, BigDecimal amount, Member payer, SplitType splitType,
                       ExpenseCategory category, LocalDate spentAt) {
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.splitType = splitType;
        this.category = category;
        this.spentAt = spentAt;
        shares.clear();
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

    public LocalDate getSpentAt() {
        return spentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExpenseShare> getShares() {
        return List.copyOf(shares);
    }
}
