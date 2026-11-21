package com.jxxerick.expensesplit.dto;

import java.util.List;

/** {@code nextCursor} goes in {@code ?before=} to fetch the next page; null on the last page. */
public record ActivityFeedResponse(List<ActivityResponse> items, Long nextCursor) {
}
