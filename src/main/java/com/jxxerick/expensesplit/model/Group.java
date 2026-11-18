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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Member> members = new ArrayList<>();

    protected Group() {
    }

    public Group(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public Member addMember(String memberName) {
        Member member = new Member(this, memberName);
        members.add(member);
        return member;
    }

    public boolean hasMemberNamed(String memberName) {
        return members.stream().anyMatch(m -> m.getName().equalsIgnoreCase(memberName.trim()));
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Member> getMembers() {
        return List.copyOf(members);
    }
}
