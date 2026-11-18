package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.AddMemberRequest;
import com.jxxerick.expensesplit.dto.CreateGroupRequest;
import com.jxxerick.expensesplit.dto.GroupResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.model.Member;
import com.jxxerick.expensesplit.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request) {
        Group group = new Group(request.name().trim());

        Set<String> seen = new HashSet<>();
        for (String name : request.members()) {
            if (!seen.add(name.trim().toLowerCase())) {
                throw new BusinessException("Membro '%s' repetido".formatted(name.trim()));
            }
            group.addMember(name);
        }
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional(readOnly = true)
    public GroupResponse findById(Long id) {
        return GroupResponse.from(getGroup(id));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> findAll() {
        return groupRepository.findAllByOrderByIdAsc().stream().map(GroupResponse::from).toList();
    }

    @Transactional
    public MemberResponse addMember(Long groupId, AddMemberRequest request) {
        Group group = getGroup(groupId);
        if (group.hasMemberNamed(request.name())) {
            throw new BusinessException("Já existe um membro chamado '%s'".formatted(request.name().trim()));
        }
        Member member = group.addMember(request.name());
        groupRepository.saveAndFlush(group);
        return MemberResponse.from(member);
    }

    /** Loads a group with its members or fails with 404. */
    @Transactional(readOnly = true)
    public Group getGroup(Long id) {
        return groupRepository.findWithMembersById(id).orElseThrow(() -> ResourceNotFoundException.group(id));
    }
}
