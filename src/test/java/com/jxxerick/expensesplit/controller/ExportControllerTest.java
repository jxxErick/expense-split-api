package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.service.LedgerExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@Import(ExportControllerTest.FixedClock.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private LedgerExportService exportService;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void downloadsCsvWithBomAndFilename() throws Exception {
        when(exportService.exportCsv(1L)).thenReturn("data;tipo\r\n");

        byte[] body = mvc.perform(get("/api/groups/1/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"group-1-2026-11-26.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("﻿data;tipo\r\n");
    }

    @Test
    void returns404ForUnknownGroup() throws Exception {
        when(exportService.exportCsv(9L)).thenThrow(ResourceNotFoundException.group(9L));

        mvc.perform(get("/api/groups/9/export")).andExpect(status().isNotFound());
    }
}
