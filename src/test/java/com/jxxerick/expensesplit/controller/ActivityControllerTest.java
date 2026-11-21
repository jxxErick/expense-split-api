package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ActivityFeedResponse;
import com.jxxerick.expensesplit.dto.ActivityResponse;
import com.jxxerick.expensesplit.exception.ResourceNotFoundException;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.service.ActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ActivityService activityService;

    @Test
    void returnsFeedPageWithCursor() throws Exception {
        when(activityService.feed(1L, 30L, ActivityType.SETTLEMENT_RECORDED, 10)).thenReturn(new ActivityFeedResponse(
                List.of(new ActivityResponse(29L, ActivityType.SETTLEMENT_RECORDED, "Carla pagou R$ 50.00 para Ana",
                        new BigDecimal("50.00"), 4L, Instant.parse("2026-11-21T15:30:00Z"))),
                29L));

        mvc.perform(get("/api/groups/1/activity").param("before", "30").param("type", "SETTLEMENT_RECORDED")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].summary").value("Carla pagou R$ 50.00 para Ana"))
                .andExpect(jsonPath("$.nextCursor").value(29));
    }

    @Test
    void returns404ForUnknownGroup() throws Exception {
        when(activityService.feed(9L, null, null, null)).thenThrow(ResourceNotFoundException.group(9L));

        mvc.perform(get("/api/groups/9/activity")).andExpect(status().isNotFound());
    }
}
