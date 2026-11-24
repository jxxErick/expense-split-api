package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.MemberStatementResponse;
import com.jxxerick.expensesplit.dto.StatementLineResponse;
import com.jxxerick.expensesplit.dto.StatementLineType;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.StatementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatementController.class)
class StatementControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private StatementService statementService;

    @Test
    void returnsStatementForThePeriod() throws Exception {
        StatementLineResponse line = new StatementLineResponse(LocalDate.of(2026, 11, 10), StatementLineType.EXPENSE,
                7L, "Hotel", money("90.00"), money("30.00"), money("60.00"), money("50.00"));
        when(statementService.statement(1L, 2L, FROM, TO)).thenReturn(new MemberStatementResponse(
                new MemberResponse(2L, "Ana"), FROM, TO, money("-10.00"), money("90.00"), money("30.00"),
                money("0.00"), money("0.00"), money("50.00"), List.of(line)));

        mvc.perform(get("/api/groups/1/members/2/statement").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.name").value("Ana"))
                .andExpect(jsonPath("$.openingBalance").value(-10.00))
                .andExpect(jsonPath("$.closingBalance").value(50.00))
                .andExpect(jsonPath("$.lines[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.lines[0].referenceId").value(7))
                .andExpect(jsonPath("$.lines[0].effect").value(60.00))
                .andExpect(jsonPath("$.lines[0].balance").value(50.00));
    }

    @Test
    void returns404ForUnknownMember() throws Exception {
        when(statementService.statement(1L, 9L, null, null)).thenThrow(ResourceNotFoundException.member(9L));

        mvc.perform(get("/api/groups/1/members/9/statement"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns422ForInvertedPeriod() throws Exception {
        when(statementService.statement(1L, 2L, TO, FROM)).thenThrow(new BusinessException("período inválido"));

        mvc.perform(get("/api/groups/1/members/2/statement").param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
