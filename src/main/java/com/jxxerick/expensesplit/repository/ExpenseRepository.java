package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"payer", "shares", "shares.member"})
    List<Expense> findByGroupIdOrderBySpentAtDescIdDesc(Long groupId);

    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);
}
