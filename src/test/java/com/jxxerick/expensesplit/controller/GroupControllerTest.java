package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.GroupResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.GroupService;
import com.jxxerick.expensesplit.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GroupService groupService;

    @MockBean
    private MemberService memberService;

    @Test
    void createsGroup() throws Exception {
        GroupResponse response = new GroupResponse(7L, "Viagem", "BRL",
                List.of(new MemberResponse(1L, "Ana"), new MemberResponse(2L, "Bruno")), Instant.now());
        when(groupService.create(any())).thenReturn(response);

        mvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Viagem", "members": ["Ana", "Bruno"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/groups/7"))
                .andExpect(jsonPath("$.members[1].name").value("Bruno"));
    }

    @Test
    void returnsFieldErrorsOnInvalidPayload() throws Exception {
        mvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "members": ["Ana"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("informe o nome do grupo"))
                .andExpect(jsonPath("$.errors.members").exists());
    }

    @Test
    void returns404WhenGroupIsMissing() throws Exception {
        when(groupService.findById(5L)).thenThrow(ResourceNotFoundException.group(5L));

        mvc.perform(get("/api/groups/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Grupo 5 não encontrado"));
    }

    @Test
    void returns422OnBusinessRuleViolation() throws Exception {
        when(groupService.addMember(eq(1L), any())).thenThrow(new BusinessException("Já existe"));

        mvc.perform(post("/api/groups/1/members").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ana"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void memberLeavesGroup() throws Exception {
        mvc.perform(delete("/api/groups/1/members/3")).andExpect(status().isNoContent());

        verify(memberService).leave(1L, 3L);
    }

    @Test
    void leavingWithOpenBalanceIs422() throws Exception {
        doThrow(new BusinessException("Bruno ainda deve R$ 10.00; acerte as contas antes de sair"))
                .when(memberService).leave(1L, 2L);

        mvc.perform(delete("/api/groups/1/members/2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Bruno ainda deve R$ 10.00; acerte as contas antes de sair"));
    }

    @Test
    void membersCarryTheActiveFlag() throws Exception {
        when(groupService.findById(1L)).thenReturn(new GroupResponse(1L, "Casa", "BRL",
                List.of(new MemberResponse(1L, "Ana"), new MemberResponse(2L, "Bruno", false)), Instant.now()));

        mvc.perform(get("/api/groups/1"))
                .andExpect(jsonPath("$.members[0].active").value(true))
                .andExpect(jsonPath("$.members[1].active").value(false));
    }

    @Test
    void allowsFrontEndOrigin() throws Exception {
        mvc.perform(options("/api/groups/1/members/2")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void rejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/groups")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
