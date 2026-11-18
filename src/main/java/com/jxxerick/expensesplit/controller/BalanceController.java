package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.BalanceResponse;
import com.jxxerick.expensesplit.dto.TransferResponse;
import com.jxxerick.expensesplit.service.BalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balances")
    public List<BalanceResponse> balances(@PathVariable Long groupId) {
        return balanceService.balances(groupId);
    }

    @GetMapping("/debts")
    public List<TransferResponse> simplifiedDebts(@PathVariable Long groupId) {
        return balanceService.simplifiedDebts(groupId);
    }
}
