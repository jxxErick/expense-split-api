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
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.service.currency.CurrencyConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final ApplicationEventPublisher events;

    public GroupService(GroupRepository groupRepository, ApplicationEventPublisher events) {
        this.groupRepository = groupRepository;
        this.events = events;
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest request) {
        Group group = new Group(request.name().trim(), CurrencyConverter.normalize(request.currencyOrDefault()));

        Set<String> seen = new HashSet<>();
        for (String name : request.members()) {
            if (!seen.add(name.trim().toLowerCase())) {
                throw new BusinessException("Membro '%s' repetido".formatted(name.trim()));
            }
            group.addMember(name);
        }
        Group saved = groupRepository.save(group);
        events.publishEvent(GroupActivityEvent.groupCreated(saved));
        return GroupResponse.from(saved);
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
        events.publishEvent(GroupActivityEvent.memberAdded(group, member));
        return MemberResponse.from(member);
    }

    /** Loads a group with its members or fails with 404. */
    @Transactional(readOnly = true)
    public Group getGroup(Long id) {
        return groupRepository.findWithMembersById(id).orElseThrow(() -> ResourceNotFoundException.group(id));
    }
}
