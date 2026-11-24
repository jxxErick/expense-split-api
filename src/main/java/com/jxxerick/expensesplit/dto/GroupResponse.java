package com.jxxerick.expensesplit.dto;

import com.jxxerick.expensesplit.model.Group;

import java.time.Instant;
import java.util.List;

public record GroupResponse(Long id, String name, String currency, List<MemberResponse> members, Instant createdAt) {

    public static GroupResponse from(Group group) {
        List<MemberResponse> members = group.getMembers().stream().map(MemberResponse::from).toList();
        return new GroupResponse(group.getId(), group.getName(), group.getCurrency(), members, group.getCreatedAt());
    }
}
