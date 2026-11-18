package com.jxxerick.expensesplit.support;

import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds entities with ids, as if they were already persisted. */
public final class TestData {

    private TestData() {
    }

    public static Group group(Long id, String name, String... memberNames) {
        Group group = new Group(name);
        ReflectionTestUtils.setField(group, "id", id);
        long memberId = 1;
        for (String memberName : memberNames) {
            Member member = group.addMember(memberName);
            ReflectionTestUtils.setField(member, "id", memberId++);
        }
        return group;
    }

    public static Member member(Group group, Long id) {
        return group.findMember(id).orElseThrow();
    }
}
