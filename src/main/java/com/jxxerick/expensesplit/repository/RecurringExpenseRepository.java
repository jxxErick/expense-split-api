package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.RecurringExpense;
import com.jxxerick.expensesplit.model.RecurringStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    @EntityGraph(attributePaths = {"payer", "shares"})
    List<RecurringExpense> findByGroupIdOrderByIdAsc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "shares"})
    Optional<RecurringExpense> findByIdAndGroupId(Long id, Long groupId);

    @EntityGraph(attributePaths = {"group", "payer", "shares"})
    List<RecurringExpense> findByStatusAndNextRunOnLessThanEqualOrderByIdAsc(RecurringStatus status, LocalDate date);
}
