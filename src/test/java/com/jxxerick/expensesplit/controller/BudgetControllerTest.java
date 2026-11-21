package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.BudgetResponse;
import com.jxxerick.expensesplit.dto.BudgetStatusResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.BudgetLevel;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BudgetController.class)
class BudgetControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BudgetService budgetService;

    @Test
    void upsertsBudgetForScope() throws Exception {
        when(budgetService.upsert(eq(1L), eq("FOOD"), any()))
                .thenReturn(new BudgetResponse(3L, "FOOD", ExpenseCategory.FOOD, new BigDecimal("300.00")));

        mvc.perform(put("/api/groups/1/budgets/FOOD").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\": 300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("FOOD"))
                .andExpect(jsonPath("$.monthlyLimit").value(300.00));
    }

    @Test
    void rejectsNonPositiveLimit() throws Exception {
        mvc.perform(put("/api/groups/1/budgets/TOTAL").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.monthlyLimit").value("deve ser maior que zero"));
        verifyNoInteractions(budgetService);
    }

    @Test
    void returns422ForUnknownScope() throws Exception {
        when(budgetService.upsert(eq(1L), eq("PETS"), any()))
                .thenThrow(new BusinessException("Escopo de orçamento inválido: PETS"));

        mvc.perform(put("/api/groups/1/budgets/PETS").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\": 10}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void parsesMonthForStatus() throws Exception {
        when(budgetService.status(1L, YearMonth.of(2026, 10))).thenReturn(new BudgetStatusResponse("2026-10", 31, 31,
                List.of(new BudgetStatusResponse.Item("TOTAL", null, new BigDecimal("500.00"),
                        new BigDecimal("450.00"), new BigDecimal("50.00"), new BigDecimal("90.00"), BudgetLevel.WARNING,
                        new BigDecimal("450.00"), BudgetLevel.WARNING, null))));

        mvc.perform(get("/api/groups/1/budgets/status").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.budgets[0].level").value("WARNING"));
    }

    @Test
    void deletesBudget() throws Exception {
        mvc.perform(delete("/api/groups/1/budgets/FOOD")).andExpect(status().isNoContent());
        verify(budgetService).delete(1L, "FOOD");
    }
}
