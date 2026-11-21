package com.jxxerick.expensesplit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Turns due recurring expenses into real expenses once a day. */
@Component
public class RecurringExpenseScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseScheduler.class);

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseScheduler(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @Scheduled(cron = "${app.recurring.cron:0 0 6 * * *}")
    public void generateDueExpenses() {
        int created = recurringExpenseService.generateAllDue();
        if (created > 0) {
            log.info("Generated {} expense(s) from recurring templates", created);
        }
    }
}
