package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.RecurringExpenseResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.RecurrenceFrequency;
import com.jxxerick.expensesplit.model.RecurringStatus;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.service.RecurringExpenseService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringExpenseController.class)
class RecurringExpenseControllerTest {

    private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RecurringExpenseService recurringExpenseService;

    @Test
    void createsRecurringExpense() throws Exception {
        when(recurringExpenseService.create(eq(1L), any())).thenReturn(response(RecurringStatus.ACTIVE));

        mvc.perform(post("/api/groups/1/recurring-expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Aluguel", "amount": 1500.00, "payerId": 1, "splitType": "EQUAL",
                                 "category": "BILLS", "frequency": "MONTHLY", "startOn": "%s",
                                 "shares": [{"memberId": 1}, {"memberId": 2}]}
                                """.formatted(NEXT_MONTH)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.nextRunOn").value(NEXT_MONTH.toString()));
    }

    @Test
    void rejectsMissingFrequencyAndPastStart() throws Exception {
        mvc.perform(post("/api/groups/1/recurring-expenses").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Aluguel", "amount": 1500.00, "payerId": 1, "splitType": "EQUAL",
                                 "startOn": "2020-01-01", "shares": [{"memberId": 1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.frequency").value("informe a frequência"))
                .andExpect(jsonPath("$.errors.startOn").value("não pode estar no passado"));
        verifyNoInteractions(recurringExpenseService);
    }

    @Test
    void returns422WhenResumingFinishedTemplate() throws Exception {
        when(recurringExpenseService.resume(1L, 4L))
                .thenThrow(new BusinessException("Essa despesa recorrente já terminou"));

        mvc.perform(post("/api/groups/1/recurring-expenses/4/resume"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void runReturnsGeneratedExpenses() throws Exception {
        when(recurringExpenseService.runDue(1L)).thenReturn(List.of(new ExpenseResponse(7L, "Aluguel",
                new BigDecimal("1500.00"), new MemberResponse(1L, "Ana"), SplitType.EQUAL, ExpenseCategory.BILLS,
                LocalDate.of(2026, 11, 21), List.of())));

        mvc.perform(post("/api/groups/1/recurring-expenses/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].spentAt").value("2026-11-21"));
    }

    @Test
    void changesExchangeRate() throws Exception {
        RecurringExpenseResponse streaming = new RecurringExpenseResponse(5L, "Streaming", new BigDecimal("15.99"),
                "USD", new BigDecimal("5.40"), new BigDecimal("86.35"), new MemberResponse(1L, "Ana"), SplitType.EQUAL,
                ExpenseCategory.LEISURE, RecurrenceFrequency.MONTHLY, NEXT_MONTH, null, RecurringStatus.ACTIVE,
                NEXT_MONTH, 2, List.of());
        when(recurringExpenseService.changeExchangeRate(1L, 5L, new BigDecimal("5.40"))).thenReturn(streaming);

        mvc.perform(put("/api/groups/1/recurring-expenses/5/exchange-rate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exchangeRate": 5.40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.convertedAmount").value(86.35));
    }

    @Test
    void rejectsMissingExchangeRate() throws Exception {
        mvc.perform(put("/api/groups/1/recurring-expenses/5/exchange-rate").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.exchangeRate").value("informe a cotação"));
        verifyNoInteractions(recurringExpenseService);
    }

    @Test
    void deletesTemplate() throws Exception {
        mvc.perform(delete("/api/groups/1/recurring-expenses/4"))
                .andExpect(status().isNoContent());
        verify(recurringExpenseService).delete(1L, 4L);
    }

    private static RecurringExpenseResponse response(RecurringStatus status) {
        return new RecurringExpenseResponse(4L, "Aluguel", new BigDecimal("1500.00"), "BRL", null,
                new BigDecimal("1500.00"), new MemberResponse(1L, "Ana"),
                SplitType.EQUAL, ExpenseCategory.BILLS, RecurrenceFrequency.MONTHLY, NEXT_MONTH, null, status,
                NEXT_MONTH, 0, List.of(new RecurringExpenseResponse.Participant(1L, "Ana", null)));
    }
}
