package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ExpenseResponse;
import com.jxxerick.expensesplit.dto.ReceiptSplitRequest;
import com.jxxerick.expensesplit.dto.ReceiptSplitResponse;
import com.jxxerick.expensesplit.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses/receipt")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping("/preview")
    public ReceiptSplitResponse preview(@PathVariable Long groupId, @Valid @RequestBody ReceiptSplitRequest request) {
        return receiptService.preview(groupId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable Long groupId, @Valid @RequestBody ReceiptSplitRequest request) {
        return receiptService.create(groupId, request);
    }
}
