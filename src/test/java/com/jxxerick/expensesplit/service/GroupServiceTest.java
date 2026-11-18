package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.AddMemberRequest;
import com.jxxerick.expensesplit.dto.CreateGroupRequest;
import com.jxxerick.expensesplit.dto.GroupResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.Group;
import com.jxxerick.expensesplit.repository.GroupRepository;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createsGroupWithTrimmedMembers() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupResponse response = groupService.create(new CreateGroupRequest(" Viagem ", List.of("Ana ", "Bruno")));

        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Viagem");
        assertThat(response.members()).extracting(MemberResponse::name).containsExactly("Ana", "Bruno");
    }

    @Test
    void rejectsDuplicatedMembersIgnoringCase() {
        CreateGroupRequest request = new CreateGroupRequest("Casa", List.of("Ana", "ana "));

        assertThatThrownBy(() -> groupService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("repetido");
        verify(groupRepository, never()).save(any());
    }

    @Test
    void addsNewMember() {
        Group group = TestData.group(1L, "Casa", "Ana", "Bruno");
        when(groupRepository.findWithMembersById(1L)).thenReturn(Optional.of(group));

        MemberResponse response = groupService.addMember(1L, new AddMemberRequest("Carla"));

        assertThat(response.name()).isEqualTo("Carla");
        assertThat(group.getMembers()).hasSize(3);
        verify(groupRepository).saveAndFlush(group);
    }

    @Test
    void rejectsMemberWithExistingName() {
        Group group = TestData.group(1L, "Casa", "Ana", "Bruno");
        when(groupRepository.findWithMembersById(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.addMember(1L, new AddMemberRequest("BRUNO")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void failsWhenGroupDoesNotExist() {
        when(groupRepository.findWithMembersById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
