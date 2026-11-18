package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.AddMemberRequest;
import com.jxxerick.expensesplit.dto.CreateGroupRequest;
import com.jxxerick.expensesplit.dto.GroupResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse group = groupService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(group.id()).toUri();
        return ResponseEntity.created(location).body(group);
    }

    @GetMapping
    public List<GroupResponse> findAll() {
        return groupService.findAll();
    }

    @GetMapping("/{id}")
    public GroupResponse findById(@PathVariable Long id) {
        return groupService.findById(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(id, request);
    }
}
