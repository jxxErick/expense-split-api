package com.jxxerick.expensesplit.model;

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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Monthly spending limit for one category, or for the whole group when the category is null. */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(Group group, ExpenseCategory category, BigDecimal monthlyLimit) {
        this.group = group;
        this.category = category;
        changeLimit(monthlyLimit);
    }

    public void changeLimit(BigDecimal newLimit) {
        this.monthlyLimit = newLimit;
        this.updatedAt = Instant.now();
    }

    /** True for the whole-group budget. */
    public boolean isTotal() {
        return category == null;
    }

    /** Whether an expense of this category counts against the budget. */
    public boolean covers(ExpenseCategory expenseCategory) {
        return category == null || category == expenseCategory;
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
