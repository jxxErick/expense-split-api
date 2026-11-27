package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.DebtAgingResponse;
import com.jxxerick.expensesplit.dto.MemberResponse;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.DebtAgingService;
import com.jxxerick.expensesplit.service.balance.AgingBucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebtAgingController.class)
class DebtAgingControllerTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 27);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DebtAgingService debtAgingService;

    @Test
    void returnsAgedDebts() throws Exception {
        Map<AgingBucket, BigDecimal> buckets = Map.of(AgingBucket.OVER_90_DAYS, new BigDecimal("40.00"));
        when(debtAgingService.aging(1L, AS_OF)).thenReturn(new DebtAgingResponse(AS_OF, new BigDecimal("40.00"),
                buckets, List.of(new DebtAgingResponse.MemberAging(new MemberResponse(2L, "Bruno"),
                        new BigDecimal("40.00"), LocalDate.of(2026, 8, 10), 109, buckets,
                        List.of(new DebtAgingResponse.OpenDebt(LocalDate.of(2026, 8, 10), 109,
                                new BigDecimal("40.00")))))));

        mvc.perform(get("/api/groups/1/debts/aging").param("asOf", "2026-11-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(40.00))
                .andExpect(jsonPath("$.buckets.OVER_90_DAYS").value(40.00))
                .andExpect(jsonPath("$.members[0].member.name").value("Bruno"))
                .andExpect(jsonPath("$.members[0].oldestDays").value(109))
                .andExpect(jsonPath("$.members[0].openDebts[0].since").value("2026-08-10"));
    }

    @Test
    void returns422ForFutureDate() throws Exception {
        when(debtAgingService.aging(eq(1L), any()))
                .thenThrow(new BusinessException("A data de referência não pode estar no futuro"));

        mvc.perform(get("/api/groups/1/debts/aging").param("asOf", "2099-01-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns404ForUnknownGroup() throws Exception {
        when(debtAgingService.aging(8L, null)).thenThrow(ResourceNotFoundException.group(8L));

        mvc.perform(get("/api/groups/8/debts/aging"))
                .andExpect(status().isNotFound());
    }
}
