package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Member;

/** {@code active} is false for members who left the group but still appear in old expenses. */
public record MemberResponse(Long id, String name, boolean active) {

    public MemberResponse(Long id, String name) {
        this(id, name, true);
    }

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName(), member.isActive());
    }
}
