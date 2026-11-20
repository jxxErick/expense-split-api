package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.ShareResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ExpenseService expenseService;

    @Test
    void createsExpense() throws Exception {
        ExpenseResponse response = new ExpenseResponse(3L, "Mercado", new BigDecimal("60.00"),
                new MemberResponse(1L, "Ana"), SplitType.EQUAL, ExpenseCategory.GROCERIES,
                LocalDate.of(2026, 11, 17),
                List.of(new ShareResponse(1L, "Ana", new BigDecimal("30.00")),
                        new ShareResponse(2L, "Bruno", new BigDecimal("30.00"))));
        when(expenseService.create(eq(1L), any())).thenReturn(response);

        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Mercado", "amount": 60.00, "payerId": 1, "splitType": "EQUAL",
                                 "shares": [{"memberId": 1}, {"memberId": 2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[1].amount").value(30.00));
    }

    @Test
    void validatesAmountAndShares() throws Exception {
        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Mercado", "amount": 10.555, "payerId": 1, "splitType": "EQUAL", "shares": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("no máximo 2 casas decimais"))
                .andExpect(jsonPath("$.errors.shares").exists());
    }

    @Test
    void returns422WhenSplitIsInconsistent() throws Exception {
        when(expenseService.create(eq(1L), any()))
                .thenThrow(new BusinessException("As porcentagens somam 90%, mas devem somar 100%"));

        mvc.perform(post("/api/groups/1/expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Uber", "amount": 30, "payerId": 1, "splitType": "PERCENTAGE",
                                 "shares": [{"memberId": 1, "value": 60}, {"memberId": 2, "value": 30}]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("As porcentagens somam 90%, mas devem somar 100%"));
    }

    @Test
    void filtersExpensesByCategory() throws Exception {
        when(expenseService.findAll(1L, ExpenseCategory.FOOD)).thenReturn(List.of());

        mvc.perform(get("/api/groups/1/expenses").param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(expenseService).findAll(1L, ExpenseCategory.FOOD);
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        mvc.perform(get("/api/groups/1/expenses").param("category", "PETS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesExpense() throws Exception {
        ExpenseResponse response = new ExpenseResponse(3L, "Mercado", new BigDecimal("80.00"),
                new MemberResponse(2L, "Bruno"), SplitType.EXACT, ExpenseCategory.GROCERIES,
                LocalDate.of(2026, 11, 17),
                List.of(new ShareResponse(1L, "Ana", new BigDecimal("80.00"))));
        when(expenseService.update(eq(1L), eq(3L), any())).thenReturn(response);

        mvc.perform(put("/api/groups/1/expenses/3").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Mercado", "amount": 80.00, "payerId": 2, "splitType": "EXACT",
                                 "shares": [{"memberId": 1, "value": 80.00}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payer.name").value("Bruno"))
                .andExpect(jsonPath("$.shares[0].amount").value(80.00));
    }

    @Test
    void deletesExpense() throws Exception {
        mvc.perform(delete("/api/groups/1/expenses/3"))
                .andExpect(status().isNoContent());

        verify(expenseService).delete(1L, 3L);
    }
}
