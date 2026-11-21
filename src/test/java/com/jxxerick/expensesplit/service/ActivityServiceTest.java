package com.jxxerick.expensesplit.service;

import com.jxxerick.expensesplit.dto.ActivityFeedResponse;
import com.jxxerick.expensesplit.dto.ActivityResponse;
import com.jxxerick.expensesplit.model.Activity;
import com.jxxerick.expensesplit.model.ActivityType;
import com.jxxerick.expensesplit.repository.ActivityRepository;
import com.jxxerick.expensesplit.service.activity.GroupActivityEvent;
import com.jxxerick.expensesplit.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:30:00Z");

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private GroupService groupService;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, groupService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(groupService.getGroup(1L)).thenReturn(TestData.group(1L, "Casa", "Ana"));
    }

    @Test
    void recordsEventWithCurrentTime() {
        service.record(new GroupActivityEvent(1L, ActivityType.EXPENSE_CREATED, "Ana pagou R$ 10.00 em 'Pão'",
                new BigDecimal("10.00"), 7L));

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(7L);
        assertThat(captor.getValue().getSummary()).contains("Pão");
    }

    @Test
    void returnsCursorWhenThereIsAnotherPage() {
        when(activityRepository.feed(1L, null, null, PageRequest.of(0, 3))).thenReturn(activities(10, 9, 8));

        ActivityFeedResponse feed = service.feed(1L, null, null, 2);

        assertThat(feed.items()).extracting(ActivityResponse::id).containsExactly(10L, 9L);
        assertThat(feed.nextCursor()).isEqualTo(9L);
    }

    @Test
    void lastPageHasNoCursor() {
        when(activityRepository.feed(1L, 9L, ActivityType.EXPENSE_CREATED, PageRequest.of(0, 21)))
                .thenReturn(activities(8, 7));

        ActivityFeedResponse feed = service.feed(1L, 9L, ActivityType.EXPENSE_CREATED, null);

        assertThat(feed.items()).hasSize(2);
        assertThat(feed.nextCursor()).isNull();
    }

    @Test
    void clampsTheLimit() {
        when(activityRepository.feed(1L, null, null, PageRequest.of(0, ActivityService.MAX_LIMIT + 1)))
                .thenReturn(List.of());
        when(activityRepository.feed(1L, null, null, PageRequest.of(0, 2))).thenReturn(List.of());

        assertThat(service.feed(1L, null, null, 5000).items()).isEmpty();
        assertThat(service.feed(1L, null, null, -3).items()).isEmpty();
    }

    private static List<Activity> activities(long... ids) {
        return LongStream.of(ids).mapToObj(id -> {
            Activity activity = new Activity(1L, ActivityType.MEMBER_ADDED, "Membro " + id, null, null, NOW);
            ReflectionTestUtils.setField(activity, "id", id);
            return activity;
        }).toList();
    }
}
