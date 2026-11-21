package com.jxxerick.expensesplit.controller;

import com.jxxerick.expensesplit.dto.ActivityFeedResponse;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.service.ActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ActivityFeedResponse feed(@PathVariable Long groupId,
                                     @RequestParam(required = false) Long before,
                                     @RequestParam(required = false) ActivityType type,
                                     @RequestParam(required = false) Integer limit) {
        return activityService.feed(groupId, before, type, limit);
    }
}
