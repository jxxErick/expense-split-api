package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.service.LedgerExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/groups/{groupId}/export")
public class ExportController {

    private static final String UTF8_BOM = "﻿";

    private final LedgerExportService exportService;
    private final Clock clock;

    public ExportController(LedgerExportService exportService, Clock clock) {
        this.exportService = exportService;
        this.clock = clock;
    }

    /** Downloads the ledger; the BOM makes Excel read the accents as UTF-8. */
    @GetMapping(produces = "text/csv")
    public ResponseEntity<byte[]> ledger(@PathVariable Long groupId) {
        byte[] body = (UTF8_BOM + exportService.exportCsv(groupId)).getBytes(StandardCharsets.UTF_8);
        String filename = "group-%d-%s.csv".formatted(groupId, LocalDate.now(clock));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
