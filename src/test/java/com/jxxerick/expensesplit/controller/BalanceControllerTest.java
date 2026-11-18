package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.BalanceResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.TransferResponse;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BalanceService balanceService;

    @Test
    void returnsBalances() throws Exception {
        when(balanceService.balances(1L)).thenReturn(List.of(
                new BalanceResponse(1L, "Ana", new BigDecimal("50.00"), new BigDecimal("25.00"), new BigDecimal("25.00")),
                new BalanceResponse(2L, "Bruno", new BigDecimal("0.00"), new BigDecimal("25.00"), new BigDecimal("-25.00"))));

        mvc.perform(get("/api/groups/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].memberName").value("Bruno"))
                .andExpect(jsonPath("$[1].balance").value(-25.00));
    }

    @Test
    void returnsSimplifiedDebts() throws Exception {
        when(balanceService.simplifiedDebts(1L)).thenReturn(List.of(
                new TransferResponse(new MemberResponse(2L, "Bruno"), new MemberResponse(1L, "Ana"), new BigDecimal("25.00"))));

        mvc.perform(get("/api/groups/1/debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].from.name").value("Bruno"))
                .andExpect(jsonPath("$[0].to.name").value("Ana"))
                .andExpect(jsonPath("$[0].amount").value(25.00));
    }

    @Test
    void returns404ForUnknownGroup() throws Exception {
        when(balanceService.simplifiedDebts(8L)).thenThrow(ResourceNotFoundException.group(8L));

        mvc.perform(get("/api/groups/8/debts"))
                .andExpect(status().isNotFound());
    }
}
