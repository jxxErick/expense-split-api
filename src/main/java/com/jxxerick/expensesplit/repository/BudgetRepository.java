package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.Budget;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByGroupIdOrderByIdAsc(Long groupId);

    Optional<Budget> findByGroupIdAndCategory(Long groupId, ExpenseCategory category);

    Optional<Budget> findByGroupIdAndCategoryIsNull(Long groupId);
}
