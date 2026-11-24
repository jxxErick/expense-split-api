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

import java.time.Instant;

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

    /** False once the member left the group; old expenses keep pointing at them. */
    @Column(nullable = false)
    private boolean active = true;

    private Instant leftAt;

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

    public void leave(Instant now) {
        this.active = false;
        this.leftAt = now;
    }

    public void rejoin() {
        this.active = true;
        this.leftAt = null;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public String getName() {
        return name;
    }
}
