package com.jxxerick.expensesplit.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "expense_groups")
public class Group {

    public static final String DEFAULT_CURRENCY = "BRL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** ISO 4217 code every balance of the group is kept in. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Member> members = new ArrayList<>();

    protected Group() {
    }

    public Group(String name) {
        this(name, DEFAULT_CURRENCY);
    }

    public Group(String name, String currency) {
        this.name = name;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public Member addMember(String memberName) {
        Member member = new Member(this, memberName);
        members.add(member);
        return member;
    }

    public Optional<Member> findMemberNamed(String memberName) {
        return members.stream().filter(m -> m.getName().equalsIgnoreCase(memberName.trim())).findFirst();
    }

    public long activeMemberCount() {
        return members.stream().filter(Member::isActive).count();
    }

    public Optional<Member> findMember(Long memberId) {
        return members.stream().filter(m -> m.getId() != null && m.getId().equals(memberId)).findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Member> getMembers() {
        return List.copyOf(members);
    }
}
