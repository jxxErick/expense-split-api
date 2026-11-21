package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.Expense;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"payer", "shares", "shares.member"})
    List<Expense> findByGroupIdOrderBySpentAtDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "shares", "shares.member"})
    List<Expense> findByGroupIdAndCategoryOrderBySpentAtDescIdDesc(Long groupId, ExpenseCategory category);

    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);

    /** Both bounds inclusive. */
    List<Expense> findByGroupIdAndSpentAtBetween(Long groupId, LocalDate from, LocalDate to);
}
