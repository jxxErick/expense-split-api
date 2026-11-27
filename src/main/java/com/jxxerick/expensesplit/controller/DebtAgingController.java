package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.DebtAgingResponse;
import com.jxxerick.expensesplit.service.DebtAgingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/groups/{groupId}/debts/aging")
public class DebtAgingController {

    private final DebtAgingService debtAgingService;

    public DebtAgingController(DebtAgingService debtAgingService) {
        this.debtAgingService = debtAgingService;
    }

    @GetMapping
    public DebtAgingResponse aging(@PathVariable Long groupId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return debtAgingService.aging(groupId, asOf);
    }
}
