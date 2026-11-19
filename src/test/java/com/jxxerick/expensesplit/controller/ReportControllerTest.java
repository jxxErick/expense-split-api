package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.SpendingReportResponse;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MemberSpending;
import com.jxxerick.expensesplit.dto.SpendingReportResponse.MonthlyTotal;
import com.jxxerick.expensesplit.exception.BusinessException;
import com.jxxerick.expensesplit.service.ReportService;
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

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReportService reportService;

    @Test
    void returnsSpendingReportForPeriod() throws Exception {
        when(reportService.spending(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .thenReturn(new SpendingReportResponse(new BigDecimal("80.00"), 2, new BigDecimal("40.00"),
                        List.of(new MonthlyTotal("2026-11", new BigDecimal("80.00"), 2)),
                        List.of(new MemberSpending(1L, "Ana", new BigDecimal("80.00"), new BigDecimal("40.00"),
                                new BigDecimal("50.00")))));

        mvc.perform(get("/api/groups/1/reports/spending").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(80.00))
                .andExpect(jsonPath("$.months[0].month").value("2026-11"))
                .andExpect(jsonPath("$.members[0].percentage").value(50.00));
    }

    @Test
    void returns422ForInvertedPeriod() throws Exception {
        when(reportService.spending(1L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)))
                .thenThrow(new BusinessException("A data inicial deve ser anterior ou igual à final"));

        mvc.perform(get("/api/groups/1/reports/spending").param("from", "2026-12-01").param("to", "2026-11-01"))
                .andExpect(status().isUnprocessableEntity());
    }
}
