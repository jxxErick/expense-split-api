package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.GroupResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.GroupService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
