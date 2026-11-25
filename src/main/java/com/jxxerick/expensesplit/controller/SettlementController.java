package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.CreateSettlementRequest;
import com.jxxerick.expensesplit.dto.SettleUpRequest;
import com.jxxerick.expensesplit.dto.SettlementResponse;
import com.jxxerick.expensesplit.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse create(@PathVariable Long groupId, @Valid @RequestBody CreateSettlementRequest request) {
        return settlementService.create(groupId, request);
    }

    /** Pays off every debt of the group following the simplified plan. */
    @PostMapping("/settle-up")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SettlementResponse> settleUp(@PathVariable Long groupId,
                                             @Valid @RequestBody(required = false) SettleUpRequest request) {
        return settlementService.settleUp(groupId, request == null ? null : request.paidAt());
    }

    @GetMapping
    public List<SettlementResponse> findAll(@PathVariable Long groupId) {
        return settlementService.findAll(groupId);
    }
}
