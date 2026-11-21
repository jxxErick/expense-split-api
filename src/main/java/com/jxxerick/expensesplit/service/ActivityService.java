package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.ActivityFeedResponse;
import com.jxxerick.expensesplit.dto.ActivityResponse;
import com.jxxerick.expensesplit.model.Activity;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.repository.ActivityRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class ActivityService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final ActivityRepository activityRepository;
    private final GroupService groupService;
    private final Clock clock;

    public ActivityService(ActivityRepository activityRepository, GroupService groupService, Clock clock) {
        this.activityRepository = activityRepository;
        this.groupService = groupService;
        this.clock = clock;
    }

    /** Runs inside the publisher's transaction, so a rolled back change leaves no trace in the feed. */
    @EventListener
    public void record(GroupActivityEvent event) {
        activityRepository.save(new Activity(event.groupId(), event.type(), event.summary(), event.amount(),
                event.referenceId(), clock.instant()));
    }

    /** Cursor pagination: fetches one extra row to know whether there is a next page. */
    @Transactional(readOnly = true)
    public ActivityFeedResponse feed(Long groupId, Long before, ActivityType type, Integer limit) {
        groupService.getGroup(groupId);
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);

        List<Activity> rows = activityRepository.feed(groupId, before, type, PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        List<ActivityResponse> items = rows.stream().limit(size).map(ActivityResponse::from).toList();
        Long nextCursor = hasMore ? items.getLast().id() : null;
        return new ActivityFeedResponse(items, nextCursor);
    }
}
