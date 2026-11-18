package com.jxxerick.expensesplit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 60)
    private String name;

    protected Member() {
    }

    Member(Group group, String name) {
        this.group = group;
        this.name = name.trim();
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }
}
