package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.Group;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @EntityGraph(attributePaths = "members")
    Optional<Group> findWithMembersById(Long id);

    @EntityGraph(attributePaths = "members")
    List<Group> findAllByOrderByIdAsc();
}
