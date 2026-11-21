package com.jxxerick.expensesplit.repository;

import com.jxxerick.expensesplit.model.Activity;
import com.jxxerick.expensesplit.model.ActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** Newest first; {@code before} is the cursor (exclusive) and both filters are optional. */
    @Query("""
            select a from Activity a
            where a.groupId = :groupId
              and (:before is null or a.id < :before)
              and (:type is null or a.type = :type)
            order by a.id desc""")
    List<Activity> feed(@Param("groupId") Long groupId, @Param("before") Long before,
                        @Param("type") ActivityType type, Pageable pageable);
}
