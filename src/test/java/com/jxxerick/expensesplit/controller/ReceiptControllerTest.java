package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse.MemberBillResponse;
import com.jxxerick.expensesplit.dto.ShareResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.model.ExpenseCategory;
import com.jxxerick.expensesplit.model.SplitType;
import com.jxxerick.expensesplit.service.ReceiptService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceiptController.class)
class ReceiptControllerTest {

    private static final String BODY = """
            {"description": "Pizzaria", "payerId": 1, "serviceChargePercent": 10,
             "items": [{"description": "Pizza", "unitPrice": 60.00, "memberIds": [1, 2]},
                       {"description": "Suco", "unitPrice": 8.00, "quantity": 2, "memberIds": [2]}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReceiptService receiptService;

    @Test
    void previewsTheSplit() throws Exception {
        when(receiptService.preview(eq(1L), any())).thenReturn(new ReceiptSplitResponse(
                new BigDecimal("76.00"), new BigDecimal("7.60"), new BigDecimal("0.00"), new BigDecimal("83.60"),
                List.of(new MemberBillResponse(1L, "Ana", new BigDecimal("30.00"), new BigDecimal("3.00"),
                                new BigDecimal("0.00"), new BigDecimal("33.00")),
                        new MemberBillResponse(2L, "Bruno", new BigDecimal("46.00"), new BigDecimal("4.60"),
                                new BigDecimal("0.00"), new BigDecimal("50.60")))));

        mvc.perform(post("/api/groups/1/expenses/receipt/preview").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(83.60))
                .andExpect(jsonPath("$.members[1].name").value("Bruno"))
                .andExpect(jsonPath("$.members[1].serviceCharge").value(4.60));
    }

    @Test
    void createsTheExpense() throws Exception {
        when(receiptService.create(eq(1L), any())).thenReturn(new ExpenseResponse(7L, "Pizzaria",
                new BigDecimal("83.60"), new MemberResponse(1L, "Ana"), SplitType.EXACT, ExpenseCategory.FOOD,
                LocalDate.of(2026, 11, 25),
                List.of(new ShareResponse(1L, "Ana", new BigDecimal("33.00")),
                        new ShareResponse(2L, "Bruno", new BigDecimal("50.60")))));

        mvc.perform(post("/api/groups/1/expenses/receipt").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitType").value("EXACT"))
                .andExpect(jsonPath("$.shares[1].amount").value(50.60));
    }

    @Test
    void validatesItemsAndServiceCharge() throws Exception {
        mvc.perform(post("/api/groups/1/expenses/receipt").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Bar", "payerId": 1, "serviceChargePercent": 45,
                                 "items": [{"description": "Chopp", "unitPrice": 0, "quantity": 0, "memberIds": []}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.serviceChargePercent").value("máximo de 30%"))
                .andExpect(jsonPath("$.errors['items[0].unitPrice']").value("deve ser maior que zero"))
                .andExpect(jsonPath("$.errors['items[0].quantity']").value("mínimo de 1"))
                .andExpect(jsonPath("$.errors['items[0].memberIds']").value("informe quem consumiu"));
        verifyNoInteractions(receiptService);
    }

    @Test
    void turnsBusinessRulesInto422() throws Exception {
        when(receiptService.create(eq(1L), any()))
                .thenThrow(new BusinessException("O desconto (R$ 90.00) não pode passar do consumo (R$ 76.00)"));

        mvc.perform(post("/api/groups/1/expenses/receipt").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("O desconto (R$ 90.00) não pode passar do consumo (R$ 76.00)"));
    }
}
