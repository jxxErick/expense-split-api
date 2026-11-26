package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.dto.SettlementResponse;
import com.jxxerick.expensesplit.service.SettlementService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
class SettlementControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SettlementService settlementService;

    private final SettlementResponse settlement = new SettlementResponse(1L, new MemberResponse(2L, "Bruno"),
            new MemberResponse(1L, "Ana"), new BigDecimal("10.00"), LocalDate.of(2026, 11, 17));

    @Test
    void createsSettlement() throws Exception {
        when(settlementService.create(eq(1L), any())).thenReturn(settlement);

        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId": 2, "toMemberId": 1, "amount": 10.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.from.name").value("Bruno"))
                .andExpect(jsonPath("$.paidAt").value("2026-11-17"));
    }

    @Test
    void rejectsMalformedCurrencyAndRate() throws Exception {
        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId": 2, "toMemberId": 1, "amount": 10, "currency": "US", "exchangeRate": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").value("use o código ISO de 3 letras (ex.: USD)"))
                .andExpect(jsonPath("$.errors.exchangeRate").value("deve ser maior que zero"));
    }

    @Test
    void rejectsNonPositiveAmountAndFutureDate() throws Exception {
        mvc.perform(post("/api/groups/1/settlements").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId": 2, "toMemberId": 1, "amount": 0, "paidAt": "2999-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").value("deve ser maior que zero"))
                .andExpect(jsonPath("$.errors.paidAt").value("não pode estar no futuro"));
    }

    @Test
    void listsSettlements() throws Exception {
        when(settlementService.findAll(1L)).thenReturn(List.of(settlement));

        mvc.perform(get("/api/groups/1/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(10.00));
    }

    @Test
    void settlesUpWithoutBody() throws Exception {
        when(settlementService.settleUp(1L, null)).thenReturn(List.of(settlement));

        mvc.perform(post("/api/groups/1/settlements/settle-up"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].from.name").value("Bruno"));
    }

    @Test
    void settlesUpOnGivenDate() throws Exception {
        when(settlementService.settleUp(1L, LocalDate.of(2026, 11, 10))).thenReturn(List.of(settlement));

        mvc.perform(post("/api/groups/1/settlements/settle-up").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidAt": "2026-11-10"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void settleUpRejectsFutureDate() throws Exception {
        mvc.perform(post("/api/groups/1/settlements/settle-up").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paidAt": "2999-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paidAt").value("não pode estar no futuro"));
    }
}
