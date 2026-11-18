package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Member;

public record MemberResponse(Long id, String name) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName());
    }
}
